package com.example.zyncwave2.ui.theme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zyncwave2.R
import com.example.zyncwave2.data.PlayerState
import com.example.zyncwave2.data.Songs
import com.example.zyncwave2.presentation.PlayerViewModel

@Composable
fun AlbumScreen(
    songs: List<Songs>,
    onSongClick: (songs: List<Songs>, position: Int) -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    val albumMap = remember(songs) {
        songs.groupBy { it.albumName ?: "Desconocido" }
    }

    val selectedAlbum by PlayerState.selectedAlbum.collectAsState()

    BackHandler(enabled = selectedAlbum != null) {
        PlayerState.selectedAlbum.value = null
    }

    if (selectedAlbum == null) {
        LazyColumn(modifier = Modifier.fillMaxSize()
            .statusBarsPadding()) {
            val albums = albumMap.keys.sorted().toList()
            items(albums, key = { it }) { albumName ->
                val albumSongs = albumMap[albumName] ?: emptyList()
                val firstSong = albumSongs.first()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { PlayerState.selectedAlbum.value = albumName }
                        .padding(8.dp)
                        .height(80.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SongArtImage(
                        data = firstSong.data,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0x33000000)),
                        errorRes = R.drawable.baseline_music_note_24
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = albumName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = firstSong.artists ?: "<unknown>",
                            color = Color(0xffbbbbbb),
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${albumSongs.size} canciones",
                            color = Color(0xffbbbbbb),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    } else {
        val albumSongs = albumMap[selectedAlbum] ?: emptyList()
        val albumName  = selectedAlbum ?: ""

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { PlayerState.selectedAlbum.value = null }) {
                    Icon(
                        painterResource(R.drawable.outline_arrow_back_ios_new_24),
                        contentDescription = null,
                        tint = Color.White
                    )
                }
                Text(
                    text = albumName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(albumSongs, key = { _, song -> song.id }) { index, song ->
                    SongsListItem(
                        song = song,
                        onClick = {
                            playerViewModel?.setQueueSource(
                                PlayerState.QueueSource.ALBUM, albumName
                            )
                            onSongClick(albumSongs, index)
                        }
                    )
                }
            }
        }
    }
}