package com.example.zyncwave2.ui.theme

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.zyncwave2.R
import com.example.zyncwave2.data.MetadataRepository
import com.example.zyncwave2.data.Songs
import com.example.zyncwave2.data.WriteResult
import com.example.zyncwave2.data.db.AppDatabase
import com.example.zyncwave2.presentation.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun TagEditorScreen(
    song: Songs,
    onDismiss: () -> Unit,
    onSaved: (title: String, artist: String, album: String, genre: String, trackNumber: Int, discNumber: Int) -> Unit,
    playerViewModel: PlayerViewModel
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows  = false
        )
    ) {
        TagEditorContent(
            song            = song,
            onDismiss       = onDismiss,
            onSaved         = onSaved,
            playerViewModel = playerViewModel
        )
    }
}

@Composable
private fun TagEditorContent(
    song: Songs,
    onDismiss: () -> Unit,
    onSaved: (title: String, artist: String, album: String, genre: String, trackNumber: Int, discNumber: Int) -> Unit,
    playerViewModel: PlayerViewModel
) {
    val context   = LocalContext.current
    val scope     = rememberCoroutineScope()
    val metaRepo  = remember { MetadataRepository(context) }
    val extension = remember { File(song.data).extension.lowercase() }

    key(song.id) {
        var title       by remember { mutableStateOf(song.title.orEmpty()) }
        var artist      by remember { mutableStateOf(song.artists.orEmpty()) }
        var album       by remember { mutableStateOf(song.albumName.orEmpty()) }
        var genre       by remember { mutableStateOf(song.genre.orEmpty()) }
        var trackNumber by remember { mutableStateOf(song.trackNumber?.toString() ?: "") }
        var discNumber  by remember { mutableStateOf(song.discNumber?.toString() ?: "") }

        LaunchedEffect(song.id) {
            val entity = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).songDao().getById(song.id)
            }
            if (entity != null) {
                if (genre.isBlank())       genre       = entity.genre.orEmpty()
                if (trackNumber.isBlank()) trackNumber = entity.trackNumber.takeIf { it > 0 }?.toString() ?: ""
                if (discNumber.isBlank())  discNumber  = entity.discNumber.takeIf { it > 1 }?.toString() ?: ""
            }
        }

        var newArtUri by remember { mutableStateOf<Uri?>(null) }
        var isSaving  by remember { mutableStateOf(false) }
        var errorMsg  by remember { mutableStateOf<String?>(null) }

        val imagePicker = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri -> newArtUri = uri }

        fun save() {
            isSaving = true
            errorMsg = null
            // Usar el nuevo método del ViewModel
            playerViewModel.saveTagsWithPause(
                songId      = song.id,
                filePath    = song.data,
                title       = title,
                artist      = artist,
                album       = album,
                genre       = genre,
                trackNumber = trackNumber.toIntOrNull() ?: 0,
                discNumber  = discNumber.toIntOrNull() ?: 1,
                artworkUri  = newArtUri
            ) { result ->
                when (result) {
                    is WriteResult.Success -> {
                        isSaving = false
                        onSaved(title, artist, album, genre,
                            trackNumber.toIntOrNull() ?: 0,
                            discNumber.toIntOrNull() ?: 1)
                    }
                    is WriteResult.Error -> {
                        errorMsg = result.message
                        isSaving = false
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xff1e1e2e))
                .verticalScroll(rememberScrollState())
        ) {
            // ── Header ────────────────────────────────────────────────────────
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
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp,
                    modifier   = Modifier.weight(1f).padding(start = 8.dp)
                )
                TextButton(onClick = { save() }, enabled = !isSaving) {
                    Text("Guardar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            // ── Carátula ──────────────────────────────────────────────────────
            val artCacheKey = remember(newArtUri) {
                if (newArtUri != null) "new_$newArtUri" else "art_${song.albumId}"
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
                    modifier     = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                    error        = painterResource(R.drawable.baseline_music_note_24)
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
                            tint     = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Text("Cambiar carátula", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Campos de texto ───────────────────────────────────────────────
            TagField("Título",  title)  { title  = it }
            Spacer(Modifier.height(16.dp))
            TagField("Artista", artist) { artist = it }
            Spacer(Modifier.height(16.dp))
            TagField("Álbum",   album)  { album  = it }
            Spacer(Modifier.height(16.dp))
            TagField("Género",  genre)  { genre  = it }
            Spacer(Modifier.height(16.dp))

            // ── Pista y disco ─────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pista", color = Color(0x80ffffff), fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value         = trackNumber,
                        onValueChange = { trackNumber = it.filter { c -> c.isDigit() } },
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedTextColor     = Color.White,
                            unfocusedTextColor   = Color.White,
                            focusedBorderColor   = Color.White,
                            unfocusedBorderColor = Color(0x50ffffff)
                        ),
                        singleLine = true
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Disco", color = Color(0x80ffffff), fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value         = discNumber,
                        onValueChange = { discNumber = it.filter { c -> c.isDigit() } },
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedTextColor     = Color.White,
                            unfocusedTextColor   = Color.White,
                            focusedBorderColor   = Color.White,
                            unfocusedBorderColor = Color(0x50ffffff)
                        ),
                        singleLine = true
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Ruta del archivo ──────────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text("Archivo", color = Color(0x80ffffff), fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    Text(
                        text     = song.data,
                        color    = Color(0x60ffffff),
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // ── Info formato ──────────────────────────────────────────────────
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .background(Color(0x209c27b0), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(R.drawable.outline_info_24),
                    contentDescription = null,
                    tint     = Color(0xFF9c27b0),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Formato .$extension — editado con TagLib",
                    color    = Color(0xffbbbbbb),
                    fontSize = 12.sp
                )
            }

            // ── Error ─────────────────────────────────────────────────────────
            errorMsg?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    it,
                    color    = Color(0xFFe91e63),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            if (isSaving) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color    = Color(0xFF9c27b0)
                )
            }

            Spacer(Modifier.height(120.dp))
        }
    }
}

@Composable
fun TagField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(label, color = Color(0x80ffffff), fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value         = value,
            onValueChange = onValueChange,
            modifier      = Modifier.fillMaxWidth(),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedTextColor     = Color.White,
                unfocusedTextColor   = Color.White,
                focusedBorderColor   = Color.White,
                unfocusedBorderColor = Color(0x50ffffff)
            ),
            singleLine = true
        )
    }
}