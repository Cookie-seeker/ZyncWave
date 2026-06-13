package com.example.zyncwave2.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zyncwave2.R
import com.example.zyncwave2.data.PlayerState
import com.example.zyncwave2.presentation.PlayerViewModel

@Composable
fun PlayerTabContent(playerViewModel: PlayerViewModel) {
    val playerState by playerViewModel.state.collectAsState()

    if (playerState.currentSong == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xff191c1f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
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

    AnimatedVisibility(
        visible = playerState.currentSong != null,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        PlayerScreen(
            songsList    = playerState.songsList,
            initialIndex = playerState.currentIndex,
            onBack       = { },
            viewModel    = playerViewModel
        )
    }
}

@Composable
fun SongsTabContent(playerViewModel: PlayerViewModel) {
    SongsListScreen(
        innerPadding    = PaddingValues(0.dp),
        currentTab      = 1,
        onTabChange     = {},
        onSongClick = { songs, position ->
            PlayerState.navigateToPlayer.value = true
            playerViewModel.initPlayback(songs, position)
        },
        playerViewModel = playerViewModel
    )
}

@Composable
fun ListsTabContent(playerViewModel: PlayerViewModel) {
    SongsListScreen(
        innerPadding    = PaddingValues(0.dp),
        currentTab      = 2,
        onTabChange     = {},
        onSongClick = { songs, position ->
            PlayerState.navigateToPlayer.value = true
            playerViewModel.initPlayback(songs, position)
        },
        playerViewModel = playerViewModel
    )
}

@Composable
fun ArtistTabContent(playerViewModel: PlayerViewModel) {
    SongsListScreen(
        innerPadding    = PaddingValues(0.dp),
        currentTab      = 3,
        onTabChange     = {},
        onSongClick = { songs, position ->
            PlayerState.navigateToPlayer.value = true
            playerViewModel.initPlayback(songs, position)
        },
        playerViewModel = playerViewModel
    )
}

@Composable
fun AlbumTabContent(playerViewModel: PlayerViewModel) {
    SongsListScreen(
        innerPadding    = PaddingValues(0.dp),
        currentTab      = 4,
        onTabChange     = {},
        onSongClick = { songs, position ->
            PlayerState.navigateToPlayer.value = true
            playerViewModel.initPlayback(songs, position)
        },
        playerViewModel = playerViewModel
    )
}

@Composable
fun FolderTabContent(playerViewModel: PlayerViewModel) {
    SongsListScreen(
        innerPadding    = PaddingValues(0.dp),
        currentTab      = 5,
        onTabChange     = {},
        onSongClick = { songs, position ->
            PlayerState.navigateToPlayer.value = true
            playerViewModel.initPlayback(songs, position)
        },
        playerViewModel = playerViewModel
    )
}

@Composable
fun DownloadTabContent() {
    DownloadScreen()
}