package com.example.zyncwave2.ui.theme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
fun ArtistScreen(
    songs: List<Songs>,
    onSongClick: (songs: List<Songs>, position: Int) -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    val artistMap = remember(songs) {
        songs.groupBy { it.artists ?: "<unknown>" }
    }

    val selectedArtist by PlayerState.selectedArtist.collectAsState()

    BackHandler(enabled = selectedArtist != null) {
        PlayerState.selectedArtist.value = null
    }

    if (selectedArtist == null) {
        LazyColumn(modifier = Modifier.fillMaxSize()
            .statusBarsPadding()) {
            val artists = artistMap.keys.sorted()
            items(artists, key = { it }) { artist ->
                val artistSongs = artistMap[artist] ?: emptyList()
                val firstSong = artistSongs.first()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { PlayerState.selectedArtist.value = artist }
                        .padding(8.dp)
                        .heightIn(min = 72.dp),
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
                            text = artist,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${artistSongs.size} canciones",
                            color = Color(0xffbbbbbb),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    } else {
        val artistSongs = artistMap[selectedArtist] ?: emptyList()
        val artistName  = selectedArtist ?: ""

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { PlayerState.selectedArtist.value = null }) {
                    Icon(
                        painterResource(R.drawable.outline_arrow_back_ios_new_24),
                        contentDescription = null,
                        tint = Color.White
                    )
                }
                Text(
                    text = artistName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(artistSongs, key = { _, song -> song.id }) { index, song ->
                    SongsListItem(
                        song = song,
                        onClick = {
                            playerViewModel?.setQueueSource(
                                PlayerState.QueueSource.ARTIST, artistName
                            )
                            onSongClick(artistSongs, index)
                        }
                    )
                }
            }
        }
    }
}