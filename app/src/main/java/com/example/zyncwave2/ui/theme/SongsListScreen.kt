package com.example.zyncwave2.ui.theme

import android.Manifest
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.zyncwave2.R
import com.example.zyncwave2.data.PlayerState
import com.example.zyncwave2.data.Songs
import com.example.zyncwave2.presentation.PlayerViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SongsListScreen(
    innerPadding: PaddingValues,
    currentTab: Int,
    onTabChange: (Int) -> Unit,
    onSongClick: (songs: List<Songs>, position: Int) -> Unit,
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel? = null
) {
    val context = LocalContext.current

    // Observa PlayerState.songsList reactivamente — se recompone cuando DownloadService lo actualiza
    val songsState by PlayerState.songsList.collectAsState()




    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionState = rememberPermissionState(permission)

    // Solo carga canciones si la lista está vacía o cambia la carpeta seleccionada
    LaunchedEffect(permissionState.status, PlayerState.selectedFolders.value) {
        if (permissionState.status.isGranted) {

            playerViewModel?.syncLibrary()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(innerPadding)
            .statusBarsPadding()
            .background(Color(0xff191c1f))
    ) {
        if (!permissionState.status.isGranted) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = { permissionState.launchPermissionRequest() }) {
                    Text("Permitir acceso a música")
                }
            }
        } else {
            when (currentTab) {
                1 -> SongsList(
                    songs = songsState,
                    onSongClick = { pos ->
                        playerViewModel?.setQueueSource(
                            PlayerState.QueueSource.ALL_SONGS, ""
                        )
                        onSongClick(songsState, pos)
                    },
                    modifier = Modifier.fillMaxSize()
                )
                2 -> ListsScreen(
                    songs           = songsState,
                    onSongClick     = onSongClick,
                    playerViewModel = playerViewModel
                )
                3 -> ArtistScreen(
                    songs           = songsState,
                    onSongClick     = onSongClick,
                    playerViewModel = playerViewModel
                )
                4 -> AlbumScreen(
                    songs           = songsState,
                    onSongClick     = onSongClick,
                    playerViewModel = playerViewModel
                )
                5 -> FolderScreen(
                    songs           = songsState,
                    onSongClick     = onSongClick,
                    playerViewModel = playerViewModel
                )
            }
        }
    }
}

@Composable
fun NowPlayingCard(
    song: Songs,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x40ffffff)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    song.albumId
                ),
                contentDescription = null,
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                error = painterResource(R.drawable.baseline_music_note_24)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title ?: "", color = Color.White, fontWeight = FontWeight.Bold)
                Text(song.artists ?: "", color = Color.White.copy(alpha = 0.7f))
            }
            Icon(
                painterResource(
                    if (isPlaying) R.drawable.baseline_pause_24
                    else R.drawable.baseline_play_arrow_24
                ),
                contentDescription = null,
                tint = Color.White
            )
        }
    }
}