package com.example.zyncwave2.ui.theme

import com.example.zyncwave2.data.getSongs
import android.Manifest
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val songsState = remember { mutableStateOf<List<Songs>>(emptyList()) }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionState = rememberPermissionState(permission)

    LaunchedEffect(
        key1 = permissionState.status,
        key2 = PlayerState.selectedFolders.value,
        key3 = PlayerState.songsList.value
    ) {
        if (permissionState.status.isGranted) {
            val songs = getSongs(context, PlayerState.selectedFolders.value)
            songsState.value = songs
            if (PlayerState.songsList.value.isEmpty()) {
                PlayerState.songsList.value = songs
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(innerPadding)
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
                    songs = songsState.value,
                    onSongClick = { pos -> onSongClick(songsState.value, pos) },
                    modifier = Modifier.fillMaxSize()
                )
                2 -> ListsScreen(
                    songs = songsState.value,
                    onSongClick = onSongClick
                )
                3 -> ArtistScreen(
                    songs = songsState.value,
                    onSongClick = onSongClick
                )
                4 -> AlbumScreen(
                    songs = songsState.value,
                    onSongClick = onSongClick
                )
                5 -> FolderScreen(
                    songs = songsState.value,
                    onSongClick = onSongClick
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