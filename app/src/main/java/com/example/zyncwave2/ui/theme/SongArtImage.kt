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
import com.example.zyncwave2.data.Songs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext


internal val artCache = object : LruCache<String, Bitmap>(
    (Runtime.getRuntime().maxMemory() / 8).toInt()
) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}


private fun decodeSampledBitmap(bytes: ByteArray, reqSize: Int = 200): Bitmap? {
    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

    var inSampleSize = 1
    val height = boundsOptions.outHeight
    val width = boundsOptions.outWidth
    if (height > reqSize || width > reqSize) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while ((halfHeight / inSampleSize) >= reqSize && (halfWidth / inSampleSize) >= reqSize) {
            inSampleSize *= 2
        }
    }

    val options = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

@Composable
fun SongArtImage(
    song: Songs,
    modifier: Modifier = Modifier,
    errorRes: Int = R.drawable.baseline_music_note_24
) {
    val data = song.data
    var bitmap by remember(data) {
        mutableStateOf(artCache.get(data))
    }

    LaunchedEffect(data, song.artworkThumb) {
        if (artCache.get(data) == null) {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    val thumb = song.artworkThumb
                    if (thumb != null) {
                        // Camino rápido: ya está guardado en Room, decodificar directo sin tocar el archivo
                        decodeSampledBitmap(thumb, reqSize = 200)
                    } else {
                        // Fallback: canciones escaneadas antes de este cambio, o sin carátula embebida
                        delay(120)
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(data)
                        val art = retriever.embeddedPicture
                        retriever.release()
                        if (art != null) decodeSampledBitmap(art, reqSize = 200) else null
                    }
                } catch (e: Exception) { null }
            }
            if (loaded != null) {
                artCache.put(data, loaded)
                bitmap = loaded
            }
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