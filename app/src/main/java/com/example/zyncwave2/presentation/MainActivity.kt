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
import com.example.zyncwave2.data.Songs
import com.example.zyncwave2.data.getSongs
import com.example.zyncwave2.ui.theme.loadSavedFolders
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private val playerViewModel: PlayerViewModel by viewModels()
    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: TabLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_ZyncWave2)
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )
        setContentView(R.layout.activity_main)


        // Insets del sistema
        WindowCompat.setDecorFitsSystemWindows(window, false)

        onBackPressedDispatcher.addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        PlayerState.isQueueExpanded -> playerViewModel.setShowQueue(false)
                        playerViewModel.handleBack() -> {}
                        PlayerState.selectedPlaylistId.value != null -> PlayerState.selectedPlaylistId.value = null
                        PlayerState.selectedSection.value != null -> PlayerState.selectedSection.value = null
                        PlayerState.selectedArtist.value != null -> PlayerState.selectedArtist.value = null
                        PlayerState.selectedAlbum.value != null -> PlayerState.selectedAlbum.value = null
                        else -> moveTaskToBack(true)
                    }
                }
            }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100
                )
            }
        }

        viewPager = findViewById(R.id.viewPager)
        bottomNav = findViewById(R.id.bottomNav)

        viewPager.adapter = MainPagerAdapter(this)
        viewPager.offscreenPageLimit = 6
        viewPager.isUserInputEnabled = true

        val tabItems = listOf(
            R.drawable.outline_play_circle_24    to "Playing",
            R.drawable.outline_library_music_24  to "Songs",
            R.drawable.outline_queue_music_24    to "Lists",
            R.drawable.outline_artist_24         to "Artists",
            R.drawable.outline_album_24          to "Albums",
            R.drawable.outline_folder_24         to "Folders",
            R.drawable.outline_download_24       to "DL"
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
                    // Volver al tab anterior visualmente
                    bottomNav.selectTab(bottomNav.getTabAt(viewPager.currentItem))
                    return
                }
                if (index == 0 && playerViewModel.state.value.currentSong == null) {
                    bottomNav.selectTab(bottomNav.getTabAt(viewPager.currentItem))
                    return
                }
                tab.text = tabTitles[index]
                viewPager.setCurrentItem(index, true)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) { tab.text = null }
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // ── Iniciar FileObserver para carpetas guardadas ───────────────────────
        val savedFolders = loadSavedFolders(this)
        if (savedFolders.isNotEmpty()) {
            PlayerState.selectedFolders.value = savedFolders
            PlayerState.startWatchingFolders(this) {
                // Esperar un momento a que MediaStore indexe el archivo nuevo
                delay(1500)
                val songs = getSongs(applicationContext, PlayerState.selectedFolders.value)
                if (songs.isNotEmpty()) {
                    PlayerState.songsList.value = songs
                    android.util.Log.d("FileObserver", "Lista actualizada: ${songs.size} canciones")
                }
            }
        }

        // ── Restaurar sesión ──────────────────────────────────────────────────
        lifecycleScope.launch {
            val session = PlayerState.loadLastSession(this@MainActivity) ?: return@launch
            val (songId, positionMs, sourceInfo) = session
            val (queueSource, queueSourceId) = sourceInfo

            val exoPlayerDeferred = async {
                var attempts = 0
                while (PlayerState.exoPlayer == null && attempts < 20) {
                    delay(100)
                    attempts++
                }
                PlayerState.exoPlayer
            }

            val allSongs = withContext(Dispatchers.IO) {
                getSongs(this@MainActivity, PlayerState.selectedFolders.value)
            }

            if (allSongs.isEmpty()) {
                PlayerState.currentSong.value = null
                return@launch
            }

            PlayerState.songsList.value = allSongs

            val queue: List<Songs> = buildQueue(queueSource, queueSourceId, allSongs)
            val finalQueue = queue.ifEmpty { allSongs }

            val index = finalQueue.indexOfFirst { it.id == songId }
                .takeIf { it >= 0 }
                ?: PlayerState.currentIndex.value.coerceIn(0, finalQueue.size - 1)

            exoPlayerDeferred.await()

            playerViewModel.setQueueSource(queueSource, queueSourceId)
            playerViewModel.initPlaybackRestored(finalQueue, index, positionMs)
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

    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            PlayerState.stopWatchingFolders()
            val position = PlayerState.exoPlayer?.currentPosition ?: 0L
            val s = playerViewModel.state.value
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
        PlayerState.QueueSource.ALL_SONGS  -> allSongs
        PlayerState.QueueSource.FAVORITES  -> allSongs.filter { FavoritesManager.isFavorite(it.id) }
        PlayerState.QueueSource.RECENT     -> allSongs.sortedByDescending { it.id }
        PlayerState.QueueSource.PLAYLIST   -> {
            val plId = queueSourceId.toLongOrNull()
            if (plId != null) PlaylistManager.getSongsForPlaylist(plId, allSongs) else allSongs
        }
        PlayerState.QueueSource.ALBUM  -> allSongs.filter { (it.albumName ?: "Desconocido") == queueSourceId }
        PlayerState.QueueSource.ARTIST -> allSongs.filter { (it.artists ?: "<unknown>") == queueSourceId }
        PlayerState.QueueSource.FOLDER -> allSongs.filter { File(it.data).parent == queueSourceId }
    }
}