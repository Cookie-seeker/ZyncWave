package com.example.zyncwave2.ui.theme

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.zyncwave2.R
import com.example.zyncwave2.data.Songs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.io.FileOutputStream

@Composable
fun TagEditorScreen(
    song: Songs,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    key(song.id) {
        var title by remember { mutableStateOf(song.title.orEmpty()) }
        var artist by remember { mutableStateOf(song.artists.orEmpty()) }
        var album by remember { mutableStateOf(song.albumName.orEmpty()) }
        var newArtUri by remember { mutableStateOf<Uri?>(null) }
        var isSaving by remember { mutableStateOf(false) }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        val imagePicker = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri -> newArtUri = uri }

        val writeRequestLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                scope.launch {
                    val extension = File(song.data).extension.lowercase()
                    val ffmpegFormats = listOf("opus", "ogg", "webm")
                    val success = if (extension in ffmpegFormats) {
                        saveTagsWithFfmpeg(context, song, title, artist, album, newArtUri)
                    } else {
                        saveTags(context, song, title, artist, album, newArtUri)
                    }
                    if (success) onSaved() else errorMsg = "Error al guardar las etiquetas"
                    isSaving = false
                }
            } else {
                isSaving = false
                errorMsg = "Permiso denegado para editar el archivo"
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC000000))
                .clickable(enabled = false) {}
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xff1e1e2e))
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painterResource(R.drawable.outline_arrow_back_ios_new_24),
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                    Text(
                        "Editar etiquetas",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                    TextButton(
                        onClick = {
                            isSaving = true
                            errorMsg = null
                            scope.launch {
                                val extension = File(song.data).extension.lowercase()
                                android.util.Log.d("TagEditor", "Guardando, extensión: $extension, ruta: ${song.data}")
                                val ffmpegFormats = listOf("opus", "ogg", "webm")

                                if (extension in ffmpegFormats) {
                                    android.util.Log.d("TagEditor", "Usando FFmpeg para: $extension")
                                    val success = saveTagsWithFfmpeg(context, song, title, artist, album, newArtUri)
                                    android.util.Log.d("TagEditor", "FFmpeg resultado: $success")
                                    if (success) onSaved()
                                    else errorMsg = "Error al guardar con FFmpeg"
                                    isSaving = false
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val success = saveTags(context, song, title, artist, album, newArtUri)
                                    if (success) onSaved()
                                    else errorMsg = "Error al guardar las etiquetas"
                                    isSaving = false
                                }
                            }
                        },
                        enabled = !isSaving
                    ) {
                        Text("Guardar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                val artCacheKey = remember(newArtUri) {
                    if (newArtUri != null) "new_${newArtUri}" else "art_${song.albumId}"
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x30ffffff))
                        .clickable { imagePicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(
                                newArtUri ?: android.content.ContentUris.withAppendedId(
                                    Uri.parse("content://media/external/audio/albumart"),
                                    song.albumId
                                )
                            )
                            .diskCacheKey(artCacheKey)
                            .memoryCacheKey(artCacheKey)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                        error = painterResource(R.drawable.baseline_music_note_24)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x60000000), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painterResource(R.drawable.outline_edit_24),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                            Text("Cambiar carátula", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                TagField(label = "Título", value = title, onValueChange = { title = it })
                Spacer(modifier = Modifier.height(16.dp))
                TagField(label = "Artista", value = artist, onValueChange = { artist = it })
                Spacer(modifier = Modifier.height(16.dp))
                TagField(label = "Álbum", value = album, onValueChange = { album = it })

                Spacer(modifier = Modifier.height(16.dp))

                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text("Archivo", color = Color(0x80ffffff), fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = song.data,
                            color = Color(0x60ffffff),
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Aviso si es formato especial
                val extension = File(song.data).extension.lowercase()
                if (extension in listOf("opus", "ogg", "webm")) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth() // ✅ fillMaxWidth para que no se corte
                            .padding(horizontal = 24.dp)
                            .background(Color(0x209c27b0), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterResource(R.drawable.outline_info_24),
                            contentDescription = null,
                            tint = Color(0xFF9c27b0),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Archivo .$extension — no se podrá editar con FFmpeg, se recomienda no editar etiquetas para dañar el archivo",
                            color = Color(0xffbbbbbb),
                            fontSize = 12.sp
                        )
                    }
                }

                errorMsg?.let {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        it,
                        color = Color(0xFFe91e63),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }

                if (isSaving) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        color = Color(0xFF9c27b0)
                    )
                }

                Spacer(modifier = Modifier.height(120.dp))
            }
        }
    }
}

@Composable
fun TagField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(label, color = Color(0x80ffffff), fontSize = 13.sp)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color(0x50ffffff)
            ),
            singleLine = true
        )
    }
}

suspend fun saveTags(
    context: Context,
    song: Songs,
    title: String,
    artist: String,
    album: String,
    artUri: Uri?
): Boolean = withContext(Dispatchers.IO) {
    try {
        val originalFile = File(song.data)
        val extension = originalFile.extension.lowercase()
        val ffmpegFormats = listOf("opus", "ogg", "webm")

        if (extension in ffmpegFormats) {
            return@withContext saveTagsWithFfmpeg(context, song, title, artist, album, artUri)
        }

        val tempFile = File(context.cacheDir, "temp_edit_${song.id}.$extension")
        originalFile.copyTo(tempFile, overwrite = true)

        val audioFile = AudioFileIO.read(tempFile)
        val tag = audioFile.tagOrCreateAndSetDefault

        tag.setField(FieldKey.TITLE, title)
        tag.setField(FieldKey.ARTIST, artist)
        tag.setField(FieldKey.ALBUM, album)

        artUri?.let { uri ->
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val artTempFile = File(context.cacheDir, "temp_art.jpg")
                FileOutputStream(artTempFile).use { out -> stream.copyTo(out) }
                val artwork = ArtworkFactory.createArtworkFromFile(artTempFile)
                tag.deleteArtworkField()
                tag.setField(artwork)
            }
        }

        audioFile.commit()

        val songUri = Uri.withAppendedPath(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            song.id.toString()
        )
        context.contentResolver.openOutputStream(songUri, "wt")?.use { out ->
            tempFile.inputStream().use { it.copyTo(out) }
        }

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.ALBUM, album)
            put(MediaStore.Audio.Media.ALBUM_ARTIST, artist)
        }
        context.contentResolver.update(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            values,
            "${MediaStore.Audio.Media._ID} = ?",
            arrayOf(song.id.toString())
        )

        android.media.MediaScannerConnection.scanFile(context, arrayOf(song.data), null, null)
        tempFile.delete()
        true
    } catch (e: Exception) {
        android.util.Log.e("TagEditor", "Error: ${e.message}", e)
        false
    }
}

suspend fun saveTagsWithFfmpeg(
    context: Context,
    song: Songs,
    title: String,
    artist: String,
    album: String,
    artUri: Uri?
): Boolean = withContext(Dispatchers.IO) {
    try {
        val originalFile = File(song.data)
        val extension = originalFile.extension.lowercase()
        val tempInput = File(context.cacheDir, "temp_input_${song.id}.$extension")
        val tempOutput = File(context.cacheDir, "temp_output_${song.id}.$extension")

        originalFile.copyTo(tempInput, overwrite = true)

        // Usar YoutubeDL con --exec para ejecutar ffmpeg
        val ffmpegPath = context.applicationInfo.nativeLibraryDir + "/libffmpeg.so"

        val metadataArgs = buildString {
            append("-i '${tempInput.absolutePath}' ")
            append("-metadata title='$title' ")
            append("-metadata artist='$artist' ")
            append("-metadata album='$album' ")
            append("-c copy ")
            append("-y '${tempOutput.absolutePath}'")
        }

        val request = com.yausername.youtubedl_android.YoutubeDLRequest("").apply {
            addOption("--exec", "$ffmpegPath $metadataArgs")
        }

        try {
            com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request)
        } catch (e: Exception) {
            android.util.Log.e("TagEditor", "YoutubeDL exec error: ${e.message}")
        }

        // Verificar si se generó el output
        if (!tempOutput.exists() || tempOutput.length() == 0L) {
            // Fallback: actualizar solo MediaStore sin tocar el archivo
            android.util.Log.d("TagEditor", "FFmpeg falló, actualizando solo MediaStore")
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Audio.Media.TITLE, title)
                put(android.provider.MediaStore.Audio.Media.ARTIST, artist)
                put(android.provider.MediaStore.Audio.Media.ALBUM, album)
            }
            context.contentResolver.update(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                values,
                "${android.provider.MediaStore.Audio.Media._ID} = ?",
                arrayOf(song.id.toString())
            )
            tempInput.delete()
            return@withContext true // Retorna true porque al menos MediaStore se actualizó
        }

        // Escribir resultado
        val songUri = Uri.withAppendedPath(
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            song.id.toString()
        )
        context.contentResolver.openOutputStream(songUri, "wt")?.use { out ->
            tempOutput.inputStream().use { it.copyTo(out) }
        }

        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Audio.Media.TITLE, title)
            put(android.provider.MediaStore.Audio.Media.ARTIST, artist)
            put(android.provider.MediaStore.Audio.Media.ALBUM, album)
        }
        context.contentResolver.update(
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            values,
            "${android.provider.MediaStore.Audio.Media._ID} = ?",
            arrayOf(song.id.toString())
        )

        android.media.MediaScannerConnection.scanFile(context, arrayOf(song.data), null, null)
        tempInput.delete()
        tempOutput.delete()
        true
    } catch (e: Exception) {
        android.util.Log.e("TagEditor", "FFmpeg error: ${e.message}", e)
        false
    }
}