package com.example.zyncwave2.ui.theme

import android.content.ContentUris
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.zyncwave2.R
import com.example.zyncwave2.data.Songs

@Composable
fun ArtistScreen(
    songs: List<Songs>,
    onSongClick: (songs: List<Songs>, position: Int) -> Unit
) {
    val artistMap = remember(songs) {
        songs.groupBy { it.artists ?: "<unknown>" }
    }

    val selectedArtist = remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = selectedArtist.value != null) {
        selectedArtist.value = null
    }

    if (selectedArtist.value == null) {
        // Pantalla de lista de artistas
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            val artists = artistMap.keys.sorted()
            items(artists.size) { index ->
                val artist = artists[index]
                val artistSongs = artistMap[artist] ?: emptyList()
                val firstSong = artistSongs.first()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedArtist.value = artist }
                        .padding(8.dp)
                        .height(72.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            firstSong.albumId
                        ),
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0x33000000)),
                        contentScale = ContentScale.Crop,
                        error = painterResource(R.drawable.outline_artist_24),
                        fallback = painterResource(R.drawable.outline_artist_24)
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
        // Pantalla de canciones del artista
        val artistSongs = artistMap[selectedArtist.value] ?: emptyList()

        Column(modifier = Modifier.fillMaxSize()) {
            // Header con botón de volver
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "← ${selectedArtist.value}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.clickable { selectedArtist.value = null }
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(artistSongs) { index, song ->
                    SongsListItem(
                        song = song,
                        onClick = { onSongClick(artistSongs, index) }
                    )
                }
            }
        }
    }
}