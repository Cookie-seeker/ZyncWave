package com.example.zyncwave2.ui.theme

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.zyncwave2.R
import com.example.zyncwave2.data.DownloadItem
import com.example.zyncwave2.data.VideoFormat
import com.example.zyncwave2.data.YtDlpManager
import com.example.zyncwave2.presentation.DownloadViewModel
import org.json.JSONArray
import org.json.JSONObject

// ── Historial (SharedPreferences) ────────────────────────────────────────────

fun saveHistory(context: Context, list: List<DownloadItem>) {
    val arr = JSONArray()
    list.forEach {
        arr.put(JSONObject().apply {
            put("id",       it.id)
            put("title",    it.title)
            put("url",      it.url)
            put("type",     it.type)
            put("status",   if (it.status == "downloading") "error" else it.status)
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

// ── DownloadScreen ────────────────────────────────────────────────────────────

@Composable
fun DownloadScreen(
    modifier: Modifier = Modifier,
    viewModel: DownloadViewModel = viewModel()
) {
    val s by viewModel.state.collectAsState()

    // Si hay formatos disponibles y showFormats=true → mostrar pantalla de formatos
    if (s.showFormats && s.availableFormats.isNotEmpty()) {
        Dialog(
            onDismissRequest = { viewModel.setShowFormats(false) },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows  = false
            )
        ) {
            FormatsScreen(
                title            = s.videoTitle,
                availableFormats = s.availableFormats,
                selectedFormat   = s.selectedFormat,
                onBack           = { viewModel.setShowFormats(false) },
                onSelectFormat   = { viewModel.selectFormat(it) }
            )
        }
    }


    if (s.showSettings) {
        Dialog(
            onDismissRequest = { viewModel.setShowSettings(false) },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows  = false
            )
        ) {
            SettingsScreen(
                state     = s,
                viewModel = viewModel,
                onBack    = { viewModel.setShowSettings(false) }
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xff191c1f), Color(0xff2c2c38))))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {

        // Header
        Row(
            modifier              = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text       = "Descargar",
                color      = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize   = 22.sp,
                fontFamily = BebasNeue
            )
            Row {
                IconButton(onClick = { viewModel.setShowSettings(true) }) {
                    Icon(
                        painterResource(R.drawable.outline_settings_24),
                        contentDescription = "Ajustes",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // ── Campo URL ─────────────────────────────────────────────────────────
        OutlinedTextField(
            value         = s.urlInput,
            onValueChange = { viewModel.onUrlChange(it) },
            label         = { Text("Pega una URL válida", color = Color.White) },
            modifier      = Modifier.fillMaxWidth(),
            enabled       = s.isBinaryReady && !s.isLoadingFormats,
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                focusedTextColor     = Color.White,
                unfocusedTextColor   = Color.White,
                cursorColor          = Color.White
            ),
            singleLine   = true,
            trailingIcon = {
                if (s.urlInput.isNotBlank() && s.isBinaryReady) {
                    if (s.isLoadingFormats) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp).padding(2.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { viewModel.onUrlChange("") }) {
                            Icon(painterResource(R.drawable.outline_delete_24), null, tint = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        )

        if (s.videoTitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(s.videoTitle, color = Color(0xffbbbbbb), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }

        if (s.selectedFormat != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x209c27b0), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(if (s.selectedFormat!!.isAudio) R.drawable.outline_library_music_24 else R.drawable.outline_play_circle_24),
                    null, tint = Color(0x30ffffff), modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "${s.selectedFormat!!.formatId} · ${s.selectedFormat!!.ext.uppercase()}",
                    color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { viewModel.setShowFormats(true) }, contentPadding = PaddingValues(0.dp)) {
                    Text("Cambiar", color = Color(0xffbbbbbb), fontSize = 11.sp)
                }
            }
        }

        if (s.statusMessage.isNotBlank() && !s.isDownloading) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(s.statusMessage, color = Color(0xFFe91e63), fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Progreso de descarga activa ───────────────────────────────────────
        if (s.isDownloading) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(s.statusMessage.take(40), color = Color(0xffbbbbbb), fontSize = 12.sp, modifier = Modifier.weight(1f), overflow = TextOverflow.Ellipsis)
                    Text("${(s.currentProgress * 100).toInt()}%", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(progress = { s.currentProgress }, modifier = Modifier.fillMaxWidth().height(6.dp), color = Color.White, trackColor = Color(0x30ffffff))
                Spacer(modifier = Modifier.height(6.dp))
                Text("Puedes salir de la app, la descarga continuará en segundo plano.", color = Color(0x80ffffff), fontSize = 11.sp)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // ── Botones ───────────────────────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick  = {
                    if (s.availableFormats.isNotEmpty()) viewModel.setShowFormats(true)
                    else viewModel.fetchFormats()
                },
                enabled  = s.isBinaryReady && s.urlInput.isNotBlank() && !s.isLoadingFormats,
                shape    = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border   = androidx.compose.foundation.BorderStroke(1.dp, Color(0x30ffffff))
            ) {
                if (s.isLoadingFormats) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(painterResource(R.drawable.outline_equalizer_24), null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ver formatos", fontSize = 13.sp)
                }
            }

            Button(
                onClick  = { viewModel.startDownload() },
                modifier = Modifier.weight(1f),
                enabled  = s.isBinaryReady && s.selectedFormat != null,
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0x30ffffff), disabledContainerColor = Color(0x0DFFFFFF))
            ) {
                Icon(painterResource(R.drawable.outline_download_24), null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (s.isDownloading || s.queue.isNotEmpty()) "Encolar" else "Descargar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        if (s.isDownloading) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick  = { viewModel.cancelDownload() },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFe91e63))
            ) {
                Icon(painterResource(R.drawable.outline_delete_24), null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cancelar descarga actual", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Cola de pendientes ────────────────────────────────────────────────
        if (s.queue.isNotEmpty()) {
            Text("En cola (${s.queue.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.queue.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x15ffffff), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(painterResource(if (item.type == "audio") R.drawable.outline_library_music_24 else R.drawable.outline_play_circle_24), null, tint = Color(0xffbbbbbb), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("En espera...", color = Color(0xffbbbbbb), fontSize = 11.sp)
                        }
                        IconButton(onClick = { viewModel.removeFromQueue(item.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(painterResource(R.drawable.outline_delete_24), null, tint = Color(0x80ffffff), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Historial ─────────────────────────────────────────────────────────
        if (s.downloadList.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Historial", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                TextButton(onClick = { viewModel.clearHistory() }) {
                    Text("Limpiar", color = Color(0x80ffffff), fontSize = 12.sp)
                }
            }
            LazyColumn {
                items(s.downloadList, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(Color(0x15ffffff), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterResource(if (item.type == "audio") R.drawable.outline_library_music_24 else R.drawable.outline_play_circle_24),
                            null,
                            tint = when (item.status) { "done" -> Color.Black; "error" -> Color(0xFFe91e63); else -> Color.White },
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(2.dp))
                            if (item.status == "downloading") {
                                LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth().height(4.dp), color = Color.White, trackColor = Color(0x30ffffff))
                            } else {
                                Text(
                                    when (item.status) { "done" -> "✓ Completado"; "error" -> "✗ Error o cancelado"; else -> "Pendiente" },
                                    color = when (item.status) { "done" -> Color.Black; "error" -> Color(0xFFe91e63); else -> Color.Gray },
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── FormatsScreen — pantalla completa con pestañas Audio / Video ──────────────

@Composable
fun FormatsScreen(
    title: String,
    availableFormats: List<VideoFormat>,
    selectedFormat: VideoFormat?,
    onBack: () -> Unit,
    onSelectFormat: (VideoFormat) -> Unit
) {
    val audioFormats   = availableFormats.filter { it.isAudio }
    val videoFormats   = availableFormats.filter { it.hasVideo }

    val suggested = listOfNotNull(
        audioFormats.maxByOrNull { it.bitrate.dropLast(1).toDoubleOrNull() ?: 0.0 },
        videoFormats.firstOrNull { it.hasAudioTrack } ?: videoFormats.firstOrNull()
    ).distinctBy { it.formatId }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Audio", "Video")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xff191c1f), Color(0xff2c2c38))))
            .statusBarsPadding()  // ← agrega esto
            .navigationBarsPadding()  // ← y esto
            .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.outline_arrow_back_ios_new_24), null, tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text("Seleccionar formato", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (title.isNotBlank()) {
                    Text(title, color = Color(0xffbbbbbb), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // ── Sugeridos ─────────────────────────────────────────────────────────
        if (suggested.isNotEmpty()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Sugerido", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggested.forEach { format ->
                        FormatItem(
                            format   = format,
                            selected = selectedFormat?.formatId == format.formatId,
                            onClick  = { onSelectFormat(format) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // ── Pestañas Audio / Video ────────────────────────────────────────────
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor   = Color.Transparent,
            contentColor     = Color.White,
            indicator        = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color    = Color.Black
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick  = { selectedTab = index },
                    text     = {
                        Text(
                            title,
                            color = if (selectedTab == index) Color.White else Color(0x80ffffff),
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        // ── Contenido de la pestaña ───────────────────────────────────────────
        when (selectedTab) {
            0 -> {
                if (audioFormats.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No hay formatos de audio disponibles", color = Color(0x80ffffff), fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier        = Modifier.weight(1f).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding  = PaddingValues(vertical = 12.dp)
                    ) {
                        items(audioFormats) { format ->
                            FormatItem(
                                format   = format,
                                selected = selectedFormat?.formatId == format.formatId,
                                onClick  = { onSelectFormat(format) }
                            )
                        }
                    }
                }
            }
            1 -> {
                if (videoFormats.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No hay formatos de video disponibles", color = Color(0x80ffffff), fontSize = 14.sp)
                    }
                } else {
                    // Sub-secciones: con audio y sin audio
                    val videoWithAudio    = videoFormats.filter { it.hasAudioTrack }
                    val videoWithoutAudio = videoFormats.filter { !it.hasAudioTrack }

                    LazyColumn(
                        modifier       = Modifier.weight(1f).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        if (videoWithAudio.isNotEmpty()) {
                            item {
                                Text("Con audio", color = Color(0xffbbbbbb), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                            }
                            items(videoWithAudio) { format ->
                                FormatItem(format = format, selected = selectedFormat?.formatId == format.formatId, onClick = { onSelectFormat(format) })
                            }
                        }
                        if (videoWithoutAudio.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Sin audio", color = Color(0xffbbbbbb), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                            }
                            items(videoWithoutAudio) { format ->
                                FormatItem(format = format, selected = selectedFormat?.formatId == format.formatId, onClick = { onSelectFormat(format) })
                            }
                        }
                    }
                }
            }
        }

        // ── Botón confirmar ───────────────────────────────────────────────────
        Button(
            onClick  = onBack,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            enabled  = selectedFormat != null,
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Color(0x30ffffff), disabledContainerColor = Color(0x0DFFFFFF))
        ) {
            Text("Confirmar formato", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

// ── FormatItem ────────────────────────────────────────────────────────────────

@Composable
fun FormatItem(format: VideoFormat, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Color(0x309c27b0) else Color(0x15ffffff), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("${format.formatId} - ${format.ext.uppercase()}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                buildString {
                    if (format.resolution.isNotBlank() && format.resolution != "audio") append("${format.resolution}  ")
                    if (format.filesize.isNotBlank()) append("${format.filesize}  ")
                    if (format.bitrate.isNotBlank()) append("${format.bitrate}Kbps")
                },
                color = Color(0xffbbbbbb), fontSize = 12.sp
            )
            if (format.note.isNotBlank()) {
                Text(format.note, color = Color(0x80ffffff), fontSize = 11.sp)
            }
        }
        if (selected) {
            Icon(painterResource(R.drawable.outline_equalizer_24), null, tint = Color.Black, modifier = Modifier.size(18.dp))
        }
    }
}

// ── SettingsScreen — pantalla completa de ajustes ─────────────────────────────

@Composable
fun SettingsScreen(
    state: com.example.zyncwave2.data.DownloadState,
    viewModel: com.example.zyncwave2.presentation.DownloadViewModel,
    onBack: () -> Unit
) {
    val s = state

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xff191c1f), Color(0xff2c2c38))))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painterResource(R.drawable.outline_arrow_back_ios_new_24),
                    contentDescription = null,
                    tint = Color.White
                )
            }
            Text(
                "Ajustes de descarga",
                color      = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize   = 18.sp,
                modifier   = Modifier.padding(start = 8.dp)
            )
        }

        // ── Sección: Preferencias de descarga ─────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(Color(0x15ffffff), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text(
                "Preferencias de descarga",
                color      = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize   = 15.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Calidad de audio
            Text("Calidad de audio", color = Color(0xffbbbbbb), fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                YtDlpManager.AudioQuality.entries.forEach { quality ->
                    FilterChip(
                        selected = s.preferences.audioQuality == quality,
                        onClick  = { viewModel.setAudioQuality(quality) },
                        label    = { Text(quality.label, fontSize = 12.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF2C2626),
                            selectedLabelColor     = Color.White,
                            containerColor         = Color(0x20ffffff),
                            labelColor             = Color(0xffbbbbbb)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Límite de velocidad
            var rateLimitInput by remember { mutableStateOf(s.preferences.rateLimit ?: "") }
            Text("Límite de velocidad", color = Color(0xffbbbbbb), fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value         = rateLimitInput,
                onValueChange = { rateLimitInput = it; viewModel.setRateLimit(it) },
                placeholder   = { Text("Ej: 2M, 500K  (vacío = sin límite)", color = Color(0x60ffffff), fontSize = 12.sp) },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Color.White,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedTextColor     = Color.White,
                    unfocusedTextColor   = Color.White,
                    cursorColor          = Color.White
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Subtítulos
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Incrustar subtítulos", color = Color(0xffbbbbbb), fontSize = 13.sp)
                    Text("Solo aplica a video", color = Color(0x60ffffff), fontSize = 11.sp)
                }
                Switch(
                    checked         = s.preferences.embedSubtitles,
                    onCheckedChange = { viewModel.setEmbedSubtitles(it) },
                    colors          = SwitchDefaults.colors(
                        checkedThumbColor   = Color.White,
                        checkedTrackColor   = Color(0xFF2C2626),
                        uncheckedThumbColor = Color(0xffbbbbbb),
                        uncheckedTrackColor = Color(0x30ffffff)
                    )
                )
            }

            if (s.preferences.embedSubtitles) {
                Spacer(modifier = Modifier.height(8.dp))
                var langInput by remember { mutableStateOf(s.preferences.subtitleLang) }
                Text("Idiomas (separados por coma)", color = Color(0xffbbbbbb), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value         = langInput,
                    onValueChange = { langInput = it; viewModel.setSubtitleLang(it) },
                    placeholder   = { Text("es,en", color = Color(0x60ffffff), fontSize = 12.sp) },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedTextColor     = Color.White,
                        unfocusedTextColor   = Color.White,
                        cursorColor          = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Sección: Actualizador yt-dlp ──────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(Color(0x15ffffff), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(R.drawable.outline_download_24),
                    contentDescription = null,
                    tint     = Color(0xFF9c27b0),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("yt-dlp", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        if (s.ytDlpVersion.isBlank()) "Obteniendo versión..." else "v${s.ytDlpVersion}",
                        color    = Color(0xffbbbbbb),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Canal de actualización", color = Color(0xffbbbbbb), fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !s.useNightly,
                    onClick  = { viewModel.setUseNightly(false) },
                    label    = { Text("Stable", fontSize = 12.sp) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF2C2626),
                        selectedLabelColor     = Color.White,
                        containerColor         = Color(0x30ffffff),
                        labelColor             = Color(0xffbbbbbb)
                    )
                )
                FilterChip(
                    selected = s.useNightly,
                    onClick  = { viewModel.setUseNightly(true) },
                    label    = { Text("Nightly", fontSize = 12.sp) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF2C2626),
                        selectedLabelColor     = Color.White,
                        containerColor         = Color(0x20ffffff),
                        labelColor             = Color(0xffbbbbbb)
                    )
                )
            }

            if (s.updateMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    s.updateMessage,
                    color    = if (s.updateMessage.startsWith("✓")) Color.White else Color(0xFFe91e63),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick  = { viewModel.updateYtDlp() },
                enabled  = s.isBinaryReady && !s.isUpdating && !s.isDownloading,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = Color(0x30ffffff),
                    disabledContainerColor = Color(0x0DFFFFFF)
                )
            ) {
                if (s.isUpdating) {
                    CircularProgressIndicator(
                        color       = Color.White,
                        modifier    = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Actualizando...", color = Color.White, fontSize = 13.sp)
                } else {
                    Icon(
                        painterResource(R.drawable.outline_download_24),
                        contentDescription = null,
                        tint     = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Actualizar (${if (s.useNightly) "Nightly" else "Stable"})",
                        color      = Color.White,
                        fontSize   = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}