package com.example.zyncwave2.ui.theme

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zyncwave2.R
import com.example.zyncwave2.data.VideoFormat
import com.example.zyncwave2.data.YtDlpManager
import com.example.zyncwave2.presentation.DownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class DownloadItem(
    val id: String,
    val title: String,
    val url: String,
    val type: String,
    val status: String = "pending",
    val progress: Float = 0f
)

fun saveHistory(context: Context, list: List<DownloadItem>) {
    val arr = JSONArray()
    list.forEach {
        arr.put(JSONObject().apply {
            put("id", it.id)
            put("title", it.title)
            put("url", it.url)
            put("type", it.type)
            put("status", if (it.status == "downloading") "error" else it.status)
            put("progress", it.progress)
        })
    }
    context.getSharedPreferences("download_prefs", Context.MODE_PRIVATE)
        .edit().putString("history", arr.toString()).apply()
}

fun loadHistory(context: Context): List<DownloadItem> {
    val json = context.getSharedPreferences("download_prefs", Context.MODE_PRIVATE)
        .getString("history", "[]") ?: "[]"
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map {
            val obj = arr.getJSONObject(it)
            DownloadItem(
                id       = obj.getString("id"),
                title    = obj.getString("title"),
                url      = obj.getString("url"),
                type     = obj.getString("type"),
                status   = obj.getString("status"),
                progress = obj.getDouble("progress").toFloat()
            )
        }
    } catch (e: Exception) { emptyList() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var urlInput          by remember { mutableStateOf("") }
    var isLoading         by remember { mutableStateOf(DownloadService.isDownloading) }
    var isLoadingFormats  by remember { mutableStateOf(false) }
    var downloadFormat    by remember { mutableStateOf("audio") }
    var videoTitle        by remember { mutableStateOf("") }
    var statusMessage     by remember { mutableStateOf("") }
    var currentProgress   by remember { mutableStateOf(0f) }
    var showFormats       by remember { mutableStateOf(false) }
    var availableFormats  by remember { mutableStateOf<List<VideoFormat>>(emptyList()) }
    var selectedFormat    by remember { mutableStateOf<VideoFormat?>(null) }
    val downloadList      = remember { mutableStateListOf<DownloadItem>() }
    var isBinaryReady     by remember { mutableStateOf(false) }

    // Cargar historial + verificar binario
    LaunchedEffect(Unit) {
        val history  = withContext(Dispatchers.IO) { loadHistory(context) }
        downloadList.addAll(history)

        // Esperar hasta 5 s a que yt-dlp esté listo (se inicializa en App.onCreate)
        var waited = 0
        while (!YtDlpManager.isBinaryInstalled(context) && waited < 50) {
            kotlinx.coroutines.delay(100)
            waited++
        }
        isBinaryReady = YtDlpManager.isBinaryInstalled(context)
        if (!isBinaryReady) statusMessage = "Error: librería no inicializada. Reinicia la app."
    }

    // Registrar callbacks del DownloadService
    DisposableEffect(Unit) {
        DownloadService.onProgress = { itemId, msg, progress ->
            // Actualizar UI desde cualquier hilo
            statusMessage   = msg
            currentProgress = progress
            val idx = downloadList.indexOfFirst { it.id == itemId }
            if (idx >= 0) downloadList[idx] = downloadList[idx].copy(progress = progress)
        }
        DownloadService.onFinished = { itemId, success ->
            val idx = downloadList.indexOfFirst { it.id == itemId }
            if (idx >= 0) {
                downloadList[idx] = downloadList[idx].copy(
                    status   = if (success) "done" else "error",
                    progress = if (success) 1f else downloadList[idx].progress
                )
            }
            saveHistory(context, downloadList.toList())
            if (success) { urlInput = ""; selectedFormat = null }
            isLoading       = false
            currentProgress = 0f
            statusMessage   = ""
        }
        onDispose {
            // Limpiar callbacks al salir del Composable (la descarga sigue en el Service)
            DownloadService.onProgress = null
            DownloadService.onFinished = null
        }
    }

    // Función para lanzar la descarga vía Service
    fun startDownload() {
        val currentUrl = urlInput.trim()
        val title      = videoTitle.ifBlank { currentUrl }
        val itemId     = System.currentTimeMillis().toString()
        val fmt        = selectedFormat

        val item = DownloadItem(itemId, title, currentUrl, downloadFormat, "downloading", 0f)
        downloadList.add(0, item)
        saveHistory(context, downloadList.toList())
        isLoading = true

        val intent = Intent(context, DownloadService::class.java).apply {
            putExtra(DownloadService.EXTRA_URL,     currentUrl)
            putExtra(DownloadService.EXTRA_ITEM_ID, itemId)
            when {
                fmt != null -> {
                    action = DownloadService.ACTION_DOWNLOAD_FORMAT
                    putExtra(DownloadService.EXTRA_FORMAT_ID, fmt.formatId)
                    putExtra(DownloadService.EXTRA_EXT,       fmt.ext.ifBlank { if (downloadFormat == "audio") "mp3" else "mp4" })
                }
                downloadFormat == "audio" -> action = DownloadService.ACTION_DOWNLOAD_AUDIO
                else                      -> action = DownloadService.ACTION_DOWNLOAD_VIDEO
            }
        }
        context.startService(intent)
    }

    fun cancelDownload() {
        context.startService(
            Intent(context, DownloadService::class.java).apply {
                action = DownloadService.ACTION_CANCEL
            }
        )
        isLoading       = false
        currentProgress = 0f
        statusMessage   = ""
        val idx = downloadList.indexOfFirst { it.status == "downloading" }
        if (idx >= 0) {
            downloadList[idx] = downloadList[idx].copy(status = "error", progress = 0f)
            saveHistory(context, downloadList.toList())
        }
    }

    // UI
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xff191c1f), Color(0xff2c2c38))
                )
            )
            .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        Text(
            text = "Descargar",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            fontFamily = BebasNeue,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Campo URL
        OutlinedTextField(
            value = urlInput,
            onValueChange = {
                urlInput        = it
                videoTitle      = ""
                selectedFormat  = null
                availableFormats = emptyList()
            },
            label = { Text("Pega una URL válida", color = Color.White) },
            modifier = Modifier.fillMaxWidth(),
            enabled = isBinaryReady && !isLoading && !isLoadingFormats,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = Color(0xFF2C2C2E),
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                focusedTextColor     = Color.White,
                unfocusedTextColor   = Color.White,
                cursorColor          = Color.White
            ),
            singleLine = true,
            trailingIcon = {
                if (urlInput.isNotBlank() && !isLoading && isBinaryReady) {
                    IconButton(onClick = {
                        scope.launch {
                            isLoadingFormats = true
                            statusMessage    = "Obteniendo formatos..."
                            val title   = YtDlpManager.getVideoTitle(context, urlInput.trim())
                            videoTitle  = title ?: ""
                            val formats = YtDlpManager.getFormats(context, urlInput.trim())
                            availableFormats = formats
                            isLoadingFormats = false
                            statusMessage    = ""
                            if (formats.isNotEmpty()) showFormats = true
                            else statusMessage = "No se pudieron obtener formatos"
                        }
                    }) {
                        if (isLoadingFormats) {
                            CircularProgressIndicator(
                                color         = Color.White,
                                modifier      = Modifier.size(20.dp),
                                strokeWidth   = 2.dp
                            )
                        } else {
                            Icon(
                                painterResource(R.drawable.outline_play_circle_24),
                                contentDescription = null,
                                tint = Color.Black
                            )
                        }
                    }
                }
            }
        )

        if (videoTitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text     = " $videoTitle",
                color    = Color(0xffbbbbbb),
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (selectedFormat != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x209c27b0), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(
                        if (downloadFormat == "audio") R.drawable.outline_library_music_24
                        else R.drawable.outline_play_circle_24
                    ),
                    contentDescription = null,
                    tint   = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text     = "Formato: ${selectedFormat!!.formatId} - ${selectedFormat!!.ext.uppercase()}",
                    color    = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick         = { showFormats = true },
                    contentPadding  = PaddingValues(0.dp)
                ) {
                    Text("Cambiar", color = Color.White, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Progreso
        if (isLoading) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement  = Arrangement.SpaceBetween,
                    verticalAlignment      = Alignment.CenterVertically
                ) {
                    Text(
                        text     = statusMessage.take(40),
                        color    = Color(0xffbbbbbb),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text       = "${(currentProgress * 100).toInt()}%",
                        color      = Color.White,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress   = { currentProgress },
                    modifier   = Modifier.fillMaxWidth().height(6.dp),
                    color      = Color.Black,
                    trackColor = Color(0x30ffffff)
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Aviso de segundo plano
                Text(
                    text     = "Puedes salir de la app, la descarga continuará en segundo plano.",
                    color    = Color(0x80ffffff),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Botones
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isLoading) {
                OutlinedButton(
                    onClick = {
                        if (availableFormats.isNotEmpty()) {
                            showFormats = true
                        } else {
                            scope.launch {
                                isLoadingFormats = true
                                statusMessage    = "Obteniendo formatos..."
                                val title   = YtDlpManager.getVideoTitle(context, urlInput.trim())
                                videoTitle  = title ?: ""
                                val formats = YtDlpManager.getFormats(context, urlInput.trim())
                                availableFormats = formats
                                isLoadingFormats = false
                                statusMessage    = ""
                                if (formats.isNotEmpty()) showFormats = true
                                else statusMessage = "No se pudieron obtener formatos"
                            }
                        }
                    },
                    enabled = isBinaryReady && urlInput.isNotBlank() && !isLoadingFormats,
                    shape   = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border  = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2C2E))
                ) {
                    if (isLoadingFormats) {
                        CircularProgressIndicator(
                            color       = Color.White,
                            modifier    = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Ver formatos", fontSize = 13.sp)
                    }
                }
            }

            Button(
                onClick = { if (isLoading) cancelDownload() else startDownload() },
                modifier = Modifier.weight(1f),
                enabled  = isBinaryReady && (isLoading || urlInput.isNotBlank()),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = if (isLoading) Color(0xFFe91e63) else Color(0xFF2C2C2E)
                )
            ) {
                Icon(
                    painterResource(
                        if (isLoading) R.drawable.outline_delete_24
                        else R.drawable.outline_download_24
                    ),
                    contentDescription = null,
                    tint     = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (isLoading) "Cancelar" else "Descargar",
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Historial
        if (downloadList.isNotEmpty()) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = "Historial",
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 16.sp
                )
                TextButton(onClick = {
                    downloadList.clear()
                    saveHistory(context, emptyList())
                }) {
                    Text("Limpiar", color = Color(0x80ffffff), fontSize = 12.sp)
                }
            }

            LazyColumn {
                items(downloadList, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(Color(0x15ffffff), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterResource(
                                if (item.type == "audio") R.drawable.outline_library_music_24
                                else R.drawable.outline_play_circle_24
                            ),
                            contentDescription = null,
                            tint = when (item.status) {
                                "done"  -> Color.Black
                                "error" -> Color(0xFFe91e63)
                                else    -> Color.White
                            },
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text       = item.title,
                                color      = Color.White,
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            if (item.status == "downloading") {
                                LinearProgressIndicator(
                                    progress   = { item.progress },
                                    modifier   = Modifier.fillMaxWidth().height(4.dp),
                                    color      = Color.White,
                                    trackColor = Color(0x30ffffff)
                                )
                            } else {
                                Text(
                                    text = when (item.status) {
                                        "done"  -> "✓ Completado"
                                        "error" -> "✗ Error o cancelado"
                                        else    -> "Pendiente"
                                    },
                                    color = when (item.status) {
                                        "done"  -> Color.Black
                                        "error" -> Color(0xFFe91e63)
                                        else    -> Color.Gray
                                    },
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // BottomSheet de formatos
    if (showFormats) {
        ModalBottomSheet(
            onDismissRequest = { showFormats = false },
            containerColor   = Color(0xff2c2c38)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                if (videoTitle.isNotBlank()) {
                    Text(
                        text       = videoTitle,
                        color      = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 15.sp,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.padding(bottom = 16.dp)
                    )
                }

                val audioFormats = availableFormats.filter { it.isAudio }
                val videoFormats = availableFormats.filter { !it.isAudio }

                if (audioFormats.isNotEmpty()) {
                    Text(
                        "Audio",
                        color      = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp,
                        modifier   = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn(
                        modifier              = Modifier.heightIn(max = 200.dp),
                        verticalArrangement   = Arrangement.spacedBy(6.dp)
                    ) {
                        items(audioFormats) { format ->
                            FormatItem(
                                format   = format,
                                selected = selectedFormat?.formatId == format.formatId,
                                onClick  = {
                                    selectedFormat = format
                                    downloadFormat = "audio"
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (videoFormats.isNotEmpty()) {
                    Text(
                        "Video",
                        color      = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp,
                        modifier   = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn(
                        modifier              = Modifier.heightIn(max = 200.dp),
                        verticalArrangement   = Arrangement.spacedBy(6.dp)
                    ) {
                        items(videoFormats) { format ->
                            FormatItem(
                                format   = format,
                                selected = selectedFormat?.formatId == format.formatId,
                                onClick  = {
                                    selectedFormat = format
                                    downloadFormat = "video"
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Button(
                    onClick  = { showFormats = false },
                    modifier = Modifier.fillMaxWidth(),
                    enabled  = selectedFormat != null,
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color.Black)
                ) {
                    Text(
                        "Confirmar formato",
                        color      = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun FormatItem(
    format   : VideoFormat,
    selected : Boolean,
    onClick  : () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) Color(0x309c27b0) else Color(0x15ffffff),
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = "${format.formatId} - ${format.ext.uppercase()}",
                color      = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize   = 14.sp
            )
            Text(
                text = buildString {
                    if (format.resolution.isNotBlank() && format.resolution != "audio") append("${format.resolution}  ")
                    if (format.filesize.isNotBlank()) append("${format.filesize}  ")
                    if (format.bitrate.isNotBlank()) append("${format.bitrate}Kbps")
                },
                color    = Color(0xffbbbbbb),
                fontSize = 12.sp
            )
            if (format.note.isNotBlank()) {
                Text(
                    text     = format.note,
                    color    = Color(0x80ffffff),
                    fontSize = 11.sp
                )
            }
        }
        if (selected) {
            Icon(
                painterResource(R.drawable.outline_equalizer_24),
                contentDescription = null,
                tint     = Color.Black,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}