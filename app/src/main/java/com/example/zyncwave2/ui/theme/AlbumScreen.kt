package com.example.zyncwave2.ui.theme

import android.content.ContentUris
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun AlbumScreen(
    songs: List<Songs>,
    onSongClick: (songs: List<Songs>, position: Int) -> Unit
) {
    val albumMap = remember(songs) {
        songs.groupBy { it.albumName ?: "Desconocido" }
    }

    val selectedAlbum = remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = selectedAlbum.value != null) {
        selectedAlbum.value = null
    }

    if (selectedAlbum.value == null) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            val albums = albumMap.keys.sorted().toList()
            items(albums.size) { index ->
                val albumName = albums[index]
                val albumSongs = albumMap[albumName] ?: emptyList()
                val firstSong = albumSongs.first()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedAlbum.value = albumName }
                        .padding(8.dp)
                        .height(80.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            firstSong.albumId  // usa el albumId del primer song
                        ),
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x33000000)),
                        contentScale = ContentScale.Crop,
                        error = painterResource(R.drawable.outline_album_24),
                        fallback = painterResource(R.drawable.outline_album_24)
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
        val albumSongs = albumMap[selectedAlbum.value] ?: emptyList()

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "← ${selectedAlbum.value}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.clickable { selectedAlbum.value = null }
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(albumSongs) { index, song ->
                    SongsListItem(
                        song = song,
                        onClick = { onSongClick(albumSongs, index) }
                    )
                }
            }
        }
    }
}