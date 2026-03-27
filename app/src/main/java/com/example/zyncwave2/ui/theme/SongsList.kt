package com.example.zyncwave2.ui.theme

import android.content.ContentUris
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.zyncwave2.R
import com.example.zyncwave2.data.Songs


@Composable
fun SongsList(
    songs: List<Songs>,
    onSongClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        itemsIndexed(songs, key = { _, song -> "${song.id}_${song.title}" }) { index, song ->
            SongsListItem(
                song = song,
                onClick = { onSongClick(index) }
            )
        }
    }
}

@Composable
fun SongsListItem(song: Songs, onClick: () -> Unit) {
    val context = LocalContext.current

    // Extraer carátula del archivo
    val bitmap = remember(song.data) {
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(song.data)
            val art = retriever.embeddedPicture
            retriever.release()
            if (art != null) {
                android.graphics.BitmapFactory.decodeByteArray(art, 0, art.size)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(all = 8.dp)
            .height(72.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0x33000000)),
                contentScale = ContentScale.Crop
            )
        } else {
            // Fallback al albumart de MediaStore
            AsyncImage(
                model = ContentUris.withAppendedId(
                    "content://media/external/audio/albumart".toUri(),
                    song.albumId
                ),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0x33000000)),
                contentScale = ContentScale.Crop,
                error = painterResource(id = R.drawable.baseline_music_note_24),
                fallback = painterResource(id = R.drawable.baseline_music_note_24)
            )
        }

        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = song.title.orEmpty(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artists.orEmpty(),
                color = Color(0xffbbbbbb),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}