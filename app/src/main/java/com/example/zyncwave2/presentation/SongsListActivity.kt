package com.example.zyncwave2.presentation

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import com.example.zyncwave2.R
import com.example.zyncwave2.data.EqualizerManager
import com.example.zyncwave2.data.PlayerState
import com.example.zyncwave2.data.Songs
import com.example.zyncwave2.data.getSongs
import com.example.zyncwave2.ui.theme.DownloadScreen
import com.example.zyncwave2.ui.theme.FolderPickerScreen
import com.example.zyncwave2.ui.theme.PlayerScreen
import com.example.zyncwave2.ui.theme.SongsListScreen
import com.example.zyncwave2.ui.theme.isFirstLaunch
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this) {
            moveTaskToBack(true)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(applicationContext)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }

        setContent {
            val showFolderPicker = remember { mutableStateOf(isFirstLaunch(this@MainActivity)) }

            if (showFolderPicker.value) {
                FolderPickerScreen(onFinish = { showFolderPicker.value = false })
                return@setContent
            }

            val pagerState = rememberPagerState(
                initialPage = 0,
                pageCount = { 7 }
            )
            val selectedSongs = remember { mutableStateOf<List<Songs>>(emptyList()) }
            val selectedIndex = remember { mutableStateOf(0) }
            val scope = rememberCoroutineScope()
            var playerKey by remember { mutableIntStateOf(0) }


            LaunchedEffect(Unit) {
                val song = PlayerState.currentSong.value ?: return@LaunchedEffect

                // Esperar ExoPlayer máx 2 segundos
                var attempts = 0
                while (PlayerState.exoPlayer == null && attempts < 20) {
                    delay(100)
                    attempts++
                }

                // Cargar canciones si el estado se perdió (app cerrada hace mucho)
                var songs = PlayerState.songsList.value
                if (songs.isEmpty()) {
                    songs = withContext(Dispatchers.IO) {
                        getSongs(this@MainActivity, PlayerState.selectedFolders.value)
                    }
                    if (songs.isNotEmpty()) {
                        PlayerState.songsList.value = songs
                    }
                }

                // Si no hay canciones de ninguna forma, limpiar sesión
                if (songs.isEmpty()) {
                    PlayerState.currentSong.value = null
                    return@LaunchedEffect
                }

                // Llenar selectedSongs → esto hace aparecer el PlayerScreen
                selectedIndex.value = PlayerState.currentIndex.value.coerceIn(0, songs.size - 1)
                selectedSongs.value = songs

                // Restaurar posición en ExoPlayer si está disponible
                val player = PlayerState.exoPlayer
                if (player != null && PlayerState.lastRestoredPosition > 0) {
                    try {
                        val mediaItem = MediaItem.Builder()
                            .setUri(song.data)
                            .build()
                        player.setMediaItem(mediaItem)
                        player.prepare()
                        player.seekTo(PlayerState.lastRestoredPosition)
                        // No llamamos play() → queda pausado en la posición guardada
                    } catch (e: Exception) {
                        android.util.Log.e("Session", "Error restaurando: ${e.message}")
                    }
                }
            }

            val navColors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                unselectedIconColor = Color.White.copy(alpha = 0.5f),
                selectedTextColor = Color.White,
                unselectedTextColor = Color.White.copy(alpha = 0.5f),
                indicatorColor = Color.White.copy(alpha = 0.15f)
            )

            val tabs = listOf(
                Pair(R.drawable.outline_play_circle_24, "Playing"),
                Pair(R.drawable.outline_library_music_24, "Songs"),
                Pair(R.drawable.outline_queue_music_24, "Lists"),
                Pair(R.drawable.outline_artist_24, "Artists"),
                Pair(R.drawable.outline_album_24, "Albums"),
                Pair(R.drawable.outline_folder_24, "Folders"),
                Pair(R.drawable.outline_download_24, ""),
            )

            Scaffold(
                containerColor = Color(0xff191c1f),
                bottomBar = {
                    NavigationBar(containerColor = Color(0xFF1a1a2e)) {
                        tabs.forEachIndexed { index, (icon, label) ->
                            NavigationBarItem(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    scope.launch {
                                        if (index == 0 && PlayerState.currentSong.value == null) return@launch
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                icon = { Icon(painterResource(icon), null) },
                                label = { Text(label) },
                                colors = navColors
                            )
                        }
                    }
                }
            ) { innerPadding ->
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = true,
                    beyondViewportPageCount = 2
                ) { page ->
                    when (page) {
                        0 -> {
                            when {
                                // Hay canciones cargadas → mostrar reproductor
                                selectedSongs.value.isNotEmpty() -> {
                                    key(playerKey) {
                                        PlayerScreen(
                                            songsList = selectedSongs.value,
                                            initialIndex = selectedIndex.value,
                                            onBack = {
                                                scope.launch { pagerState.animateScrollToPage(1) }
                                            }
                                        )
                                    }
                                }
                                //  Sin sesión - pantalla vacía limpia
                                else -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xff191c1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                                        ) {
                                            Icon(
                                                painterResource(R.drawable.outline_play_circle_24),
                                                contentDescription = null,
                                                tint = Color(0x40ffffff),
                                                modifier = Modifier.size(64.dp)
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                "No hay canción reproduciéndose",
                                                color = Color(0x80ffffff),
                                                fontSize = 15.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        1 -> SongsListScreen(
                            innerPadding = innerPadding,
                            currentTab = 1,
                            onTabChange = {},
                            onSongClick = { songs, position ->
                                selectedSongs.value = songs
                                selectedIndex.value = position
                                PlayerState.currentIndex.value = position
                                PlayerState.currentSong.value = songs.getOrNull(position)
                                playerKey++
                                scope.launch { pagerState.animateScrollToPage(0) }
                            }
                        )
                        2 -> SongsListScreen(
                            innerPadding = innerPadding,
                            currentTab = 2,
                            onTabChange = {},
                            onSongClick = { songs, position ->
                                selectedSongs.value = songs
                                selectedIndex.value = position
                                PlayerState.currentIndex.value = position
                                PlayerState.currentSong.value = songs.getOrNull(position)
                                playerKey++
                                scope.launch { pagerState.animateScrollToPage(0) }
                            }
                        )
                        3 -> SongsListScreen(
                            innerPadding = innerPadding,
                            currentTab = 3,
                            onTabChange = {},
                            onSongClick = { songs, position ->
                                selectedSongs.value = songs
                                selectedIndex.value = position
                                PlayerState.currentIndex.value = position
                                PlayerState.currentSong.value = songs.getOrNull(position)
                                playerKey++
                                scope.launch { pagerState.animateScrollToPage(0) }
                            }
                        )
                        4 -> SongsListScreen(
                            innerPadding = innerPadding,
                            currentTab = 4,
                            onTabChange = {},
                            onSongClick = { songs, position ->
                                selectedSongs.value = songs
                                selectedIndex.value = position
                                PlayerState.currentIndex.value = position
                                PlayerState.currentSong.value = songs.getOrNull(position)
                                playerKey++
                                scope.launch { pagerState.animateScrollToPage(0) }
                            }
                        )
                        5 -> SongsListScreen(
                            innerPadding = innerPadding,
                            currentTab = 5,
                            onTabChange = {},
                            onSongClick = { songs, position ->
                                selectedSongs.value = songs
                                selectedIndex.value = position
                                PlayerState.currentIndex.value = position
                                PlayerState.currentSong.value = songs.getOrNull(position)
                                playerKey++
                                scope.launch { pagerState.animateScrollToPage(0) }
                            }
                        )
                        6 -> DownloadScreen()
                        else -> Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color(0xff191c1f))
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            val position = PlayerState.exoPlayer?.currentPosition ?: 0L
            val song = PlayerState.currentSong.value
            android.util.Log.d("Session", "Guardando: ${song?.title} @ ${position}ms, songId=${song?.id}")
            PlayerState.saveLastSession(this, position)
            android.util.Log.d("Session", "Guardado OK")
            PlayerState.exoPlayer?.release()
            PlayerState.exoPlayer = null
            EqualizerManager.release()
        }
    }
}