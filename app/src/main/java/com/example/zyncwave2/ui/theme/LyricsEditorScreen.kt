package com.example.zyncwave2.ui.theme

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.zyncwave2.R
import com.example.zyncwave2.data.LyricsManager
import com.example.zyncwave2.data.LyricsRepository
import com.example.zyncwave2.data.Songs
import kotlinx.coroutines.launch

// ── Estados posibles de la UI de letras ──────────────────────────────────────
private sealed interface LyricsUiMode {
    data object Editor    : LyricsUiMode
    data class Searching(val source: LyricsRepository.Source) : LyricsUiMode
    data class Results(
        val items: List<LyricsRepository.LyricsResult>
    ) : LyricsUiMode
}

@Composable
fun LyricsEditorScreen(
    song: Songs,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    // ── Dialog fullscreen: tiene su propia ventana, el teclado no afecta al player ──
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows  = false
        )
    ) {
        LyricsEditorContent(song = song, onDismiss = onDismiss, onSave = onSave)
    }
}

@Composable
private fun LyricsEditorContent(
    song: Songs,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var lyricsText     by remember { mutableStateOf(LyricsManager.loadLyrics(context, song.id)) }
    var mode           by remember { mutableStateOf<LyricsUiMode>(LyricsUiMode.Editor) }
    var errorMsg       by remember { mutableStateOf<String?>(null) }
    var showSourceMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData   = data,
                    containerColor = Color(0xff2c2c38),
                    contentColor   = Color.White,
                    shape          = RoundedCornerShape(12.dp)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xff191c1f))
                .padding(innerPadding)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {

                // ── Barra superior ────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            if (mode is LyricsUiMode.Results) mode = LyricsUiMode.Editor
                            else onDismiss()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0x30ffffff), shape = CircleShape)
                    ) {
                        Icon(
                            painterResource(R.drawable.outline_arrow_back_ios_new_24),
                            contentDescription = "Atrás",
                            tint = Color.White
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text       = song.title.orEmpty(),
                            color      = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 15.sp,
                            maxLines   = 1,
                            modifier   = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                        )
                        Text(
                            text     = song.artists.orEmpty(),
                            color    = Color(0xffbbbbbb),
                            fontSize = 13.sp,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                        )
                    }

                    if (mode is LyricsUiMode.Editor) {
                        IconButton(
                            onClick = {
                                LyricsManager.saveLyrics(context, song.id, lyricsText)
                                onSave(lyricsText)
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0x30ffffff), shape = CircleShape)
                        ) {
                            Icon(
                                painterResource(R.drawable.outline_edit_note_24),
                                contentDescription = "Guardar",
                                tint = Color.White
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(44.dp))
                    }
                }

                // ── Contenido principal según modo ────────────────────────────
                when (val currentMode = mode) {

                    // ── Modo editor ───────────────────────────────────────────
                    is LyricsUiMode.Editor -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .border(1.dp, Color(0x50ffffff), RoundedCornerShape(12.dp))
                                .padding(4.dp)
                        ) {
                            if (lyricsText.isEmpty()) {
                                Text(
                                    "Escribe o pega las letras aquí...",
                                    color    = Color(0x60ffffff),
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                            TextField(
                                value         = lyricsText,
                                onValueChange = { lyricsText = it },
                                modifier      = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor   = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor   = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor        = Color.White,
                                    unfocusedTextColor      = Color.White,
                                    cursorColor             = Color.White
                                ),
                                textStyle = TextStyle(fontSize = 15.sp, lineHeight = 24.sp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        errorMsg?.let {
                            Text(
                                it,
                                color    = Color(0xFFe91e63),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        // ── Botones inferiores ────────────────────────────────
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Buscar en Google
                            Button(
                                onClick = {
                                    val query = java.net.URLEncoder.encode(
                                        "${song.title} ${song.artists} letras", "UTF-8"
                                    )
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://www.google.com/search?q=$query")
                                    )
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f),
                                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xff2c2c38)),
                                shape    = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    painterResource(R.drawable.outline_search_24), null,
                                    tint = Color.White, modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Google", color = Color.White)
                            }

                            // ── Botón Buscar con dropdown ─────────────────────
                            Box {
                                Button(
                                    onClick = { showSourceMenu = true },
                                    colors  = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xff2c2c38)
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        painterResource(R.drawable.outline_search_24), null,
                                        tint = Color.White, modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Buscar", color = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        painterResource(R.drawable.outline_more_horiz_24), null,
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded         = showSourceMenu,
                                    onDismissRequest = { showSourceMenu = false },
                                    modifier         = Modifier.background(Color(0xff2c2c38))
                                ) {
                                    Text(
                                        "Buscar letras en…",
                                        color    = Color(0x80ffffff),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                    HorizontalDivider(color = Color(0x20ffffff))

                                    LyricsRepository.Source.entries.forEach { source ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        source.label,
                                                        color      = Color.White,
                                                        fontWeight = FontWeight.Medium,
                                                        fontSize   = 14.sp
                                                    )
                                                    Text(
                                                        when (source) {
                                                            LyricsRepository.Source.YTMUSIC -> "Letras vía Youtube Music"
                                                            LyricsRepository.Source.LRCLIB    -> "Letras sincronizadas y planas"
                                                            LyricsRepository.Source.LYRICSOVH -> "Letras planas"
                                                            LyricsRepository.Source.GENIUS    -> "Letras planas"
                                                            LyricsRepository.Source.NETEASE   -> "Letras sincronizadas, pop global y asiático"
                                                        },
                                                        color    = Color(0x80ffffff),
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    painterResource(
                                                        when (source) {
                                                            LyricsRepository.Source.YTMUSIC -> R.drawable.outline_music_note_2_24
                                                            LyricsRepository.Source.LRCLIB    -> R.drawable.outline_lyrics_24
                                                            LyricsRepository.Source.LYRICSOVH -> R.drawable.outline_search_24
                                                            LyricsRepository.Source.GENIUS    -> R.drawable.outline_mic_24
                                                            LyricsRepository.Source.NETEASE   -> R.drawable.outline_music_note_24
                                                        }
                                                    ),
                                                    contentDescription = null,
                                                    tint = when (source) {
                                                        LyricsRepository.Source.YTMUSIC -> Color(0xFFFF0000)
                                                        LyricsRepository.Source.LRCLIB    -> Color(0xFF3949AB)
                                                        LyricsRepository.Source.LYRICSOVH -> Color(0xFF00bcd4)
                                                        LyricsRepository.Source.GENIUS    -> Color(0xFFC0CA33)
                                                        LyricsRepository.Source.NETEASE   -> Color(0xFFe91e63)
                                                    },
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            },
                                            onClick = {
                                                showSourceMenu = false
                                                errorMsg       = null
                                                mode           = LyricsUiMode.Searching(source)
                                                scope.launch {
                                                    val result = LyricsRepository.search(
                                                        title  = song.title.orEmpty(),
                                                        artist = song.artists.orEmpty(),
                                                        source = source
                                                    )
                                                    result.fold(
                                                        onSuccess = { items ->
                                                            if (items.isEmpty()) {
                                                                errorMsg = "No se encontraron letras en ${source.label}"
                                                                mode = LyricsUiMode.Editor
                                                            } else {
                                                                mode = LyricsUiMode.Results(items)
                                                            }
                                                        },
                                                        onFailure = {
                                                            errorMsg = "Error al conectar con ${source.label}"
                                                            mode = LyricsUiMode.Editor
                                                        }
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            // Pegar del portapapeles
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                            as ClipboardManager
                                    val clip = clipboard.primaryClip?.getItemAt(0)
                                    if (clip != null) {
                                        val text = clip.coerceToText(context).toString()
                                        if (text.isNotBlank()) lyricsText = text
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xff2c2c38), shape = RoundedCornerShape(10.dp))
                            ) {
                                Icon(
                                    painterResource(R.drawable.outline_content_paste_24), null,
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // ── Modo buscando ─────────────────────────────────────────
                    is LyricsUiMode.Searching -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color       = Color.White,
                                    modifier    = Modifier.size(48.dp),
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Buscando en ${currentMode.source.label}…",
                                    color    = Color(0xffbbbbbb),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    // ── Modo resultados ───────────────────────────────────────
                    is LyricsUiMode.Results -> {
                        Text(
                            "${currentMode.items.size} resultado${if (currentMode.items.size != 1) "s" else ""}",
                            color    = Color(0xffbbbbbb),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(currentMode.items) { result ->
                                LyricsResultItem(
                                    result       = result,
                                    onPickPlain  = {
                                        scope.launch {
                                            val resolved = LyricsRepository.fetchLyricsIfNeeded(result)
                                            val lyrics   = resolved.plainLyrics
                                            if (lyrics == null) {
                                                snackbarHostState.showSnackbar(
                                                    message  = "No hay letras disponibles en la base de datos de ${result.source.label}",
                                                    duration = SnackbarDuration.Short
                                                )
                                                return@launch
                                            }
                                            LyricsManager.saveLyrics(context, song.id, lyrics)
                                            lyricsText = lyrics
                                            mode       = LyricsUiMode.Editor
                                            onSave(lyrics)
                                        }
                                    },
                                    onPickSynced = {
                                        scope.launch {
                                            val resolved = LyricsRepository.fetchLyricsIfNeeded(result)
                                            val lyrics   = resolved.syncedLyrics
                                            if (lyrics == null) {
                                                snackbarHostState.showSnackbar(
                                                    message  = "No hay letras disponibles en la base de datos de ${result.source.label}",
                                                    duration = SnackbarDuration.Short
                                                )
                                                return@launch
                                            }
                                            LyricsManager.saveLyrics(context, song.id, lyrics)
                                            lyricsText = lyrics
                                            mode       = LyricsUiMode.Editor
                                            onSave(lyrics)
                                        }
                                    }
                                )
                                HorizontalDivider(color = Color(0x20ffffff))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Tarjeta de resultado unificada ───────────────────────────────────────────

@Composable
private fun LyricsResultItem(
    result: LyricsRepository.LyricsResult,
    onPickPlain: () -> Unit,
    onPickSynced: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(
                        when (result.source) {
                            LyricsRepository.Source.YTMUSIC -> Color(0x30FF0000)
                            LyricsRepository.Source.LRCLIB    -> Color(0x30ffffff)
                            LyricsRepository.Source.LYRICSOVH -> Color(0x3000bcd4)
                            LyricsRepository.Source.GENIUS    -> Color(0x30c0ca33)
                            LyricsRepository.Source.NETEASE   -> Color(0x30e91e63)
                        },
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    result.source.label,
                    color = when (result.source) {
                        LyricsRepository.Source.YTMUSIC -> Color(0xFFFF0000)
                        LyricsRepository.Source.LRCLIB    -> Color(0xCCffffff)
                        LyricsRepository.Source.LYRICSOVH -> Color(0xFF00bcd4)
                        LyricsRepository.Source.GENIUS    -> Color(0xFFC0CA33)
                        LyricsRepository.Source.NETEASE   -> Color(0xFFe91e63)
                    },
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(result.title, color = Color.White, fontWeight = FontWeight.Bold,
            fontSize = 14.sp, maxLines = 1)
        Text(result.artistName, color = Color(0xffbbbbbb), fontSize = 12.sp, maxLines = 1)

        Row(modifier = Modifier.padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (result.albumName.isNotBlank()) {
                Text(result.albumName, color = Color(0x80ffffff), fontSize = 11.sp,
                    maxLines = 1, modifier = Modifier.weight(1f))
            }
            if (result.duration.isNotBlank()) {
                Text(result.duration, color = Color(0x80ffffff), fontSize = 11.sp)
            }
        }

        Row(modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val hasOrCanFetchSynced = result.syncedLyrics != null ||
                    result.source == LyricsRepository.Source.NETEASE ||
                    result.source == LyricsRepository.Source.YTMUSIC


            if (hasOrCanFetchSynced) {
                Button(
                    onClick = onPickSynced,
                    colors  = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                    shape   = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(painterResource(R.drawable.outline_lyrics_24), null,
                        tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sincronizadas", color = Color.White, fontSize = 12.sp)
                }
            }

            val hasOrCanFetchPlain = result.plainLyrics != null ||
                    result.source == LyricsRepository.Source.NETEASE ||
                    result.source == LyricsRepository.Source.GENIUS ||
                    result.source == LyricsRepository.Source.YTMUSIC


            if (hasOrCanFetchPlain) {
                Button(
                    onClick = onPickPlain,
                    colors  = ButtonDefaults.buttonColors(containerColor = Color(0xff2c2c38)),
                    shape   = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(painterResource(R.drawable.outline_edit_note_24), null,
                        tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Planas", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}