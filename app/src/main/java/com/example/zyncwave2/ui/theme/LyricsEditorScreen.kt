package com.example.zyncwave2.ui.theme

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zyncwave2.R
import com.example.zyncwave2.data.LyricsManager
import com.example.zyncwave2.data.Songs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import androidx.compose.foundation.layout.imePadding

@Composable
fun LyricsEditorScreen(
    song: Songs,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val context = LocalContext.current
    var lyricsText by remember { mutableStateOf(LyricsManager.loadLyrics(context, song.id)) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xff191c1f))
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            // Barra superior
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0x30ffffff), shape = CircleShape)
                ) {
                    Icon(
                        painterResource(R.drawable.outline_arrow_back_ios_new_24),
                        contentDescription = "Cerrar",
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
                        text = song.title ?: "",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    )
                    Text(
                        text = song.artists ?: "",
                        color = Color(0xffbbbbbb),
                        fontSize = 13.sp,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    )
                }

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
            }

            // Área de texto
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(
                        width = 1.dp,
                        color = Color(0x50ffffff),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(4.dp)
            ) {
                if (lyricsText.isEmpty()) {
                    Text(
                        text = "Escribe o pega las letras aquí...",
                        color = Color(0x60ffffff),
                        fontSize = 15.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                TextField(
                    value = lyricsText,
                    onValueChange = { lyricsText = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF9c27b0)
                    ),
                    textStyle = TextStyle(fontSize = 15.sp, lineHeight = 24.sp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mensaje de error
            searchError?.let {
                Text(
                    text = it,
                    color = Color(0xFFe91e63),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Botones inferiores
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val artist = song.artists?.trim().orEmpty()
                        val title = song.title?.trim().orEmpty()
                        val query = URLEncoder.encode("$title $artist letras", "UTF-8")
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://www.google.com/search?q=$query")
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xff2c2c38)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isSearching
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color(0xFF9c27b0),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            painterResource(R.drawable.outline_search_24),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isSearching) "Buscando..." else "Buscar en línea",
                        color = Color.White
                    )
                }

                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as ClipboardManager
                        val clip = clipboard.primaryClip?.getItemAt(0)
                        if (clip != null) {

                            val text = clip.coerceToText(context).toString()
                            if (text.isNotBlank()) {
                                lyricsText = text
                            }
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xff2c2c38), shape = RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        painterResource(R.drawable.outline_content_paste_24),
                        contentDescription = "Pegar",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(120.dp))
        }
    }
}

suspend fun searchLyricsOnline(artist: String, title: String): String? =
    withContext(Dispatchers.IO) {
        try {
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val url = "https://api.lyrics.ovh/v1/$encodedArtist/$encodedTitle"
            val response = URL(url).readText()
            val json = JSONObject(response)
            val lyrics = json.optString("lyrics", "")
            if (lyrics.isBlank()) null else lyrics.trim()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }