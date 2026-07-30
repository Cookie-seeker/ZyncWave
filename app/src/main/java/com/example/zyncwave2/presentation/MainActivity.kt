package com.example.zyncwave2.presentation

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.zyncwave2.R
import com.example.zyncwave2.data.EqualizerManager
import com.example.zyncwave2.data.FavoritesManager
import com.example.zyncwave2.data.PlayerState
import com.example.zyncwave2.data.PlaylistManager
import com.example.zyncwave2.data.SongRepository
import com.example.zyncwave2.data.Songs
import com.example.zyncwave2.data.db.AppDatabase
import com.example.zyncwave2.ui.theme.loadSavedFolders
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private val playerViewModel: PlayerViewModel by viewModels()
    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: TabLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        android.util.Log.d("PERF", "MainActivity.onCreate START: ${System.currentTimeMillis()}")
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_ZyncWave2)
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )
        setContentView(R.layout.activity_main)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        onBackPressedDispatcher.addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        PlayerState.isQueueExpanded                  -> playerViewModel.setShowQueue(false)
                        playerViewModel.handleBack()                 -> {}
                        PlayerState.selectedPlaylistId.value != null -> PlayerState.selectedPlaylistId.value = null
                        PlayerState.selectedSection.value != null    -> PlayerState.selectedSection.value = null
                        PlayerState.selectedArtist.value != null     -> PlayerState.selectedArtist.value = null
                        PlayerState.selectedAlbum.value != null      -> PlayerState.selectedAlbum.value = null
                        else -> moveTaskToBack(true)
                    }
                }
            }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }

        viewPager = findViewById(R.id.viewPager)
        bottomNav = findViewById(R.id.bottomNav)

        viewPager.adapter            = MainPagerAdapter(this)
        viewPager.offscreenPageLimit = 1
        viewPager.isUserInputEnabled = true

        val tabItems = listOf(
            R.drawable.outline_play_circle_24   to "Playing",
            R.drawable.outline_library_music_24 to "Songs",
            R.drawable.outline_queue_music_24   to "Lists",
            R.drawable.outline_artist_24        to "Artists",
            R.drawable.outline_album_24         to "Albums",
            R.drawable.outline_folder_24        to "Folders",
            R.drawable.outline_download_24      to "DL"
        )

        tabItems.forEachIndexed { index, (icon, title) ->
            val tab = bottomNav.newTab().setIcon(icon)
            if (index == 0) tab.setText(title) else tab.text = null
            bottomNav.addTab(tab)
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                bottomNav.selectTab(bottomNav.getTabAt(position))
            }
        })

        val tabTitles = tabItems.map { it.second }

        bottomNav.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val index = tab.position
                if (index == 6) {
                    startActivity(Intent(this@MainActivity, DownloadActivity::class.java))
                    bottomNav.selectTab(bottomNav.getTabAt(viewPager.currentItem))
                    return
                }
                tab.text = tabTitles[index]
                viewPager.setCurrentItem(index, true)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) { tab.text = null }
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // FileObserver — syncWithDisk en lugar de getSongs
        val savedFolders = loadSavedFolders(this)
        if (savedFolders.isNotEmpty()) {
            PlayerState.selectedFolders.value = savedFolders
            PlayerState.startWatchingFolders(this) {
                delay(1500)
                withContext(Dispatchers.IO) {
                    SongRepository(applicationContext)
                        .syncWithDisk(PlayerState.selectedFolders.value)
                }
            }
        }

        // Restaurar sesión con flujo optimista:
        // 1. Room (~10ms) → datos listos
        // 2. preloadSongMeta() → UI visible INMEDIATAMENTE
        // 3. Navegar al tab 0
        // 4. ExoPlayer en background → conectar audio sin bloquear UI
        lifecycleScope.launch {
            val session = PlayerState.loadLastSession(this@MainActivity) ?: return@launch
            val (songId, positionMs, sourceInfo) = session
            val (queueSource, queueSourceId) = sourceInfo

            val allSongs = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(this@MainActivity)
                    .songDao()
                    .getAllFlow()
                    .first()
                    .filter { entity ->
                        PlayerState.selectedFolders.value.isEmpty() ||
                                PlayerState.selectedFolders.value.any {
                                    entity.data.startsWith(it)
                                }
                    }
                    .map { it.toSongs() }
            }

            if (allSongs.isEmpty()) return@launch

            PlayerState.songsList.value = allSongs

            val queue      = buildQueue(queueSource, queueSourceId, allSongs)
            val finalQueue = queue.ifEmpty { allSongs }
            val index      = finalQueue.indexOfFirst { it.id == songId }
                .takeIf { it >= 0 }
                ?: PlayerState.currentIndex.value.coerceIn(0, finalQueue.size - 1)

            // UI inmediata — sin esperar ExoPlayer
            playerViewModel.setQueueSource(queueSource, queueSourceId)
            playerViewModel.preloadSongMeta(finalQueue, index)
            android.util.Log.d("PERF", "MainActivity preloadSongMeta DONE: ${System.currentTimeMillis()}")
            viewPager.post {
                viewPager.setCurrentItem(0, false)
            }

            // ExoPlayer en background — no bloquea la UI
            launch(Dispatchers.IO) {
                var attempts = 0
                while (PlayerState.exoPlayer == null && attempts < 20) {
                    delay(100)
                    attempts++
                }
                withContext(Dispatchers.Main) {
                    playerViewModel.initPlaybackRestored(finalQueue, index, positionMs)
                }
            }
        }

        // Room Flow — fuente de verdad para la lista de canciones
        lifecycleScope.launch {
            AppDatabase.getInstance(this@MainActivity)
                .songDao()
                .getAllFlow()
                .collect { entities ->
                    val songs = entities
                        .filter { entity ->
                            PlayerState.selectedFolders.value.isEmpty() ||
                                    PlayerState.selectedFolders.value.any {
                                        entity.data.startsWith(it)
                                    }
                        }
                        .map { it.toSongs() }
                    if (songs.isNotEmpty()) {
                        PlayerState.songsList.value = songs
                    }
                }
        }

        lifecycleScope.launch {
            PlayerState.navigateToPlayer.collect { shouldNavigate ->
                if (shouldNavigate) {
                    viewPager.setCurrentItem(0, false)
                    PlayerState.navigateToPlayer.value = false
                }
            }
        }

        lifecycleScope.launch {
            PlayerState.showQueue.collect { show ->
                bottomNav.isEnabled = !show
            }
        }

        lifecycleScope.launch {
            playerViewModel.state.collect { state ->
                val tab0View = bottomNav.getTabAt(0)?.view
                val hasSong  = state.currentSong != null
                tab0View?.isEnabled = hasSong
                tab0View?.alpha     = if (hasSong) 1f else 0.4f
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            PlayerState.stopWatchingFolders()
            val position = PlayerState.exoPlayer?.currentPosition ?: 0L
            val s        = playerViewModel.state.value
            PlayerState.saveLastSession(
                this, position,
                s.queueSource,
                s.queueSourceId
            )
            PlayerState.exoPlayer?.release()
            PlayerState.exoPlayer = null
            EqualizerManager.release()
        }
    }

    private fun buildQueue(
        queueSource: PlayerState.QueueSource,
        queueSourceId: String,
        allSongs: List<Songs>
    ): List<Songs> = when (queueSource) {
        PlayerState.QueueSource.ALL_SONGS -> allSongs
        PlayerState.QueueSource.FAVORITES -> allSongs.filter { FavoritesManager.isFavorite(it.id) }
        PlayerState.QueueSource.RECENT    -> allSongs.sortedByDescending { it.id }
        PlayerState.QueueSource.PLAYLIST  -> {
            val plId = queueSourceId.toLongOrNull()
            if (plId != null) PlaylistManager.getSongsForPlaylist(plId, allSongs) else allSongs
        }
        PlayerState.QueueSource.ALBUM  -> allSongs.filter { (it.albumName ?: "Desconocido") == queueSourceId }
        PlayerState.QueueSource.ARTIST -> allSongs.filter { (it.artists ?: "<unknown>") == queueSourceId }
        PlayerState.QueueSource.FOLDER -> allSongs.filter { File(it.data).parent == queueSourceId }
    }
}