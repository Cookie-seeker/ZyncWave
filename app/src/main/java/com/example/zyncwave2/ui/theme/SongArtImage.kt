package com.example.zyncwave2.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.zyncwave2.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


private val artCache = LruCache<String, Bitmap>(200)

@Composable
fun SongArtImage(
    data: String,
    modifier: Modifier = Modifier,
    errorRes: Int = R.drawable.baseline_music_note_24
) {
    var bitmap by remember(data) {
        mutableStateOf(artCache.get(data))
    }

    LaunchedEffect(data) {
        if (artCache.get(data) == null && bitmap == null) {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(data)
                    val art = retriever.embeddedPicture
                    retriever.release()
                    if (art != null) BitmapFactory.decodeByteArray(art, 0, art.size) else null
                } catch (e: Exception) { null }
            }
            if (loaded != null) artCache.put(data, loaded)
            bitmap = loaded
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Image(
            painter = painterResource(errorRes),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}