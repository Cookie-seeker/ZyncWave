package com.example.zyncwave2.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(all = 8.dp)
            .heightIn(min = 72.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SongArtImage(
            data = song.data,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0x33000000)),
            errorRes = R.drawable.baseline_music_note_24
        )

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