package com.example.zyncwave2.presentation

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.zyncwave2.data.DownloadItem
import com.example.zyncwave2.data.DownloadState
import com.example.zyncwave2.data.PreferencesRepository
import com.example.zyncwave2.data.VideoFormat
import com.example.zyncwave2.data.YtDlpManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class DownloadTask(
    val itemId: String,
    val url: String,
    val preferences: YtDlpManager.DownloadPreferences,
    val title: String
)

class DownloadViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context get() = getApplication<Application>().applicationContext

    private val _state = MutableStateFlow(DownloadState())
    val state = _state.asStateFlow()

    private val downloadChannel = Channel<DownloadTask>(capacity = Channel.UNLIMITED)

    // Repositorio de preferencias — único punto de acceso al DataStore
    private val prefsRepository = PreferencesRepository(context)

    init {
        registerServiceCallbacks()
        initBinaryAndHistory()
        observePreferences()       // ← nuevo: carga preferencias persistidas
        startQueueWorker()
    }

    // ── Observar preferencias persistidas ────────────────────────────────────

    /**
     * Se suscribe al Flow del DataStore.
     * Cada vez que el usuario cambia una preferencia y se guarda,
     * el estado se actualiza automáticamente con el nuevo valor.
     * Al arrancar la app emite de inmediato con los valores guardados.
     */
    private fun observePreferences() {
        viewModelScope.launch {
            prefsRepository.preferencesFlow.collect { savedPrefs ->
                _state.update { it.copy(preferences = savedPrefs) }
            }
        }
    }

    // ── Worker de la cola ─────────────────────────────────────────────────────

    private fun startQueueWorker() {
        viewModelScope.launch {
            for (task in downloadChannel) {
                val stillPending = _state.value.queue.any { it.id == task.itemId }
                if (!stillPending) continue

                while (_state.value.isDownloading) {
                    kotlinx.coroutines.delay(300)
                }
                dispatchToService(task)
            }
        }
    }

    private fun dispatchToService(task: DownloadTask) {
        _state.update { current ->
            val newItem = DownloadItem(
                id       = task.itemId,
                title    = task.title,
                url      = task.url,
                type     = if (task.preferences.isAudio) "audio" else "video",
                status   = "downloading",
                progress = 0f
            )
            current.copy(
                isDownloading = true,
                queue         = current.queue.filter { it.id != task.itemId },
                downloadList  = listOf(newItem) + current.downloadList
            )
        }
        saveHistory()

        val prefs = task.preferences
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_DOWNLOAD_FORMAT
            putExtra(DownloadService.EXTRA_URL,             task.url)
            putExtra(DownloadService.EXTRA_ITEM_ID,         task.itemId)
            putExtra(DownloadService.EXTRA_FORMAT_ID,       prefs.formatId)
            putExtra(DownloadService.EXTRA_EXT,             prefs.ext)
            putExtra(DownloadService.EXTRA_IS_AUDIO,        prefs.isAudio)
            putExtra(DownloadService.EXTRA_AUDIO_QUALITY,   prefs.audioQuality.value)
            putExtra(DownloadService.EXTRA_RATE_LIMIT,      prefs.rateLimit ?: "")
            putExtra(DownloadService.EXTRA_EMBED_SUBTITLES, prefs.embedSubtitles)
            putExtra(DownloadService.EXTRA_SUBTITLE_LANG,   prefs.subtitleLang)
        }
        context.startService(intent)
    }

    // ── Inicialización ────────────────────────────────────────────────────────

    private fun initBinaryAndHistory() {
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) {
                com.example.zyncwave2.ui.theme.loadHistory(context)
            }
            _state.update { it.copy(downloadList = history) }

            var waited = 0
            while (!YtDlpManager.isBinaryInstalled(context) && waited < 300) {
                kotlinx.coroutines.delay(100)
                waited++
            }

            val ready = YtDlpManager.isBinaryInstalled(context)
            if (ready) {
                val version = withContext(Dispatchers.IO) {
                    YtDlpManager.getCurrentVersion(context)
                }
                _state.update { it.copy(isBinaryReady = true, ytDlpVersion = version) }
            } else {
                _state.update {
                    it.copy(
                        isBinaryReady = false,
                        statusMessage = "Error: yt-dlp no inicializado. Verifica tu conexión y reinicia."
                    )
                }
            }
        }
    }

    // ── Callbacks del Service ─────────────────────────────────────────────────

    private fun registerServiceCallbacks() {
        DownloadService.onProgress = { itemId, msg, progress ->
            _state.update { current ->
                val updatedList = current.downloadList.map { item ->
                    if (item.id == itemId) item.copy(progress = progress) else item
                }
                current.copy(
                    statusMessage   = msg,
                    currentProgress = progress,
                    downloadList    = updatedList
                )
            }
        }

        DownloadService.onFinished = { itemId, success ->
            _state.update { current ->
                val updatedList = current.downloadList.map { item ->
                    if (item.id == itemId) item.copy(
                        status   = if (success) "done" else "error",
                        progress = if (success) 1f else item.progress
                    ) else item
                }
                current.copy(
                    isDownloading    = false,
                    currentProgress  = 0f,
                    statusMessage    = "",
                    downloadList     = updatedList,
                    urlInput         = if (success) "" else current.urlInput,
                    selectedFormat   = if (success) null else current.selectedFormat,
                    availableFormats = if (success) emptyList() else current.availableFormats,
                    videoTitle       = if (success) "" else current.videoTitle
                )
            }
            saveHistory()
        }
    }

    override fun onCleared() {
        downloadChannel.close()
        DownloadService.onProgress = null
        DownloadService.onFinished = null
        super.onCleared()
    }

    // ── Acciones de la UI ─────────────────────────────────────────────────────

    fun onUrlChange(url: String) {
        _state.update {
            it.copy(
                urlInput         = url,
                videoTitle       = "",
                selectedFormat   = null,
                availableFormats = emptyList(),
                statusMessage    = ""
            )
        }
    }

    fun fetchFormats() {
        val url = _state.value.urlInput.trim()
        if (url.isBlank()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoadingFormats = true, statusMessage = "Obteniendo formatos...") }

            val title   = YtDlpManager.getVideoTitle(context, url)
            val formats = YtDlpManager.getFormats(context, url)

            _state.update { current ->
                current.copy(
                    isLoadingFormats = false,
                    videoTitle       = title ?: "",
                    availableFormats = formats,
                    statusMessage    = if (formats.isEmpty()) "No se pudieron obtener formatos. Verifica la URL." else "",
                    showFormats      = formats.isNotEmpty()
                )
            }
        }
    }

    fun selectFormat(format: VideoFormat) {
        _state.update { it.copy(selectedFormat = format, showFormats = false) }
    }

    fun setShowFormats(show: Boolean) {
        _state.update { it.copy(showFormats = show) }
    }

    fun startDownload() {
        val current = _state.value
        val fmt     = current.selectedFormat ?: return
        val url     = current.urlInput.trim()
        val title   = current.videoTitle.ifBlank { url }
        val itemId  = System.currentTimeMillis().toString()

        val prefs = current.preferences.copy(
            formatId = fmt.formatId,
            ext      = fmt.ext.ifBlank { if (fmt.isAudio) "mp3" else "mp4" },
            isAudio  = fmt.isAudio
        )

        val task = DownloadTask(
            itemId      = itemId,
            url         = url,
            preferences = prefs,
            title       = title
        )

        val pendingItem = DownloadItem(
            id       = itemId,
            title    = title,
            url      = url,
            type     = if (fmt.isAudio) "audio" else "video",
            status   = "pending",
            progress = 0f
        )
        _state.update { it.copy(queue = it.queue + pendingItem) }
        downloadChannel.trySend(task)
    }

    fun cancelDownload() {
        context.startService(
            Intent(context, DownloadService::class.java).apply {
                action = DownloadService.ACTION_CANCEL
            }
        )
        _state.update { current ->
            val updatedList = current.downloadList.map { item ->
                if (item.status == "downloading") item.copy(status = "error", progress = 0f) else item
            }
            current.copy(
                isDownloading   = false,
                currentProgress = 0f,
                statusMessage   = "",
                downloadList    = updatedList
            )
        }
        saveHistory()
    }

    fun removeFromQueue(itemId: String) {
        _state.update { it.copy(queue = it.queue.filter { item -> item.id != itemId }) }
    }

    fun clearHistory() {
        _state.update { it.copy(downloadList = emptyList()) }
        saveHistory()
    }

    // ── Preferencias — ahora persisten en DataStore ───────────────────────────

    fun setShowPreferences(show: Boolean) {
        _state.update { it.copy(showPreferences = show) }
    }

    fun setAudioQuality(quality: YtDlpManager.AudioQuality) {
        // Actualización optimista en el estado local (UI responde de inmediato)
        _state.update { it.copy(preferences = it.preferences.copy(audioQuality = quality)) }
        // Persistir en DataStore en background
        viewModelScope.launch { prefsRepository.saveAudioQuality(quality) }
    }

    fun setRateLimit(limit: String) {
        val cleaned = limit.trim().uppercase().ifBlank { null }
        _state.update { it.copy(preferences = it.preferences.copy(rateLimit = cleaned)) }
        viewModelScope.launch { prefsRepository.saveRateLimit(cleaned) }
    }

    fun setEmbedSubtitles(value: Boolean) {
        _state.update { it.copy(preferences = it.preferences.copy(embedSubtitles = value)) }
        viewModelScope.launch { prefsRepository.saveEmbedSubtitles(value) }
    }

    fun setSubtitleLang(lang: String) {
        _state.update { it.copy(preferences = it.preferences.copy(subtitleLang = lang)) }
        viewModelScope.launch { prefsRepository.saveSubtitleLang(lang) }
    }

    // ── Actualizador ──────────────────────────────────────────────────────────

    fun toggleUpdatePanel() {
        _state.update { it.copy(showUpdatePanel = !it.showUpdatePanel) }
    }

    fun setUseNightly(value: Boolean) {
        _state.update { it.copy(useNightly = value) }
    }

    fun updateYtDlp() {
        val useNightly = _state.value.useNightly
        viewModelScope.launch {
            _state.update { it.copy(isUpdating = true, updateMessage = "") }
            YtDlpManager.updateYtDlp(context, useNightly) { msg ->
                _state.update { it.copy(updateMessage = msg) }
            }
            val version = withContext(Dispatchers.IO) { YtDlpManager.getCurrentVersion(context) }
            _state.update { it.copy(isUpdating = false, ytDlpVersion = version) }
        }
    }

    // Preferencia y el actualizador en una misma sección para simplificar la UI y evitar paneles múltiples

    fun setShowSettings(show: Boolean) {
        _state.update { it.copy(showSettings = show, showPreferences = false, showUpdatePanel = false) }
    }

    // ── Historial ─────────────────────────────────────────────────────────────

    private fun saveHistory() {
        val list = _state.value.downloadList
        viewModelScope.launch(Dispatchers.IO) {
            com.example.zyncwave2.ui.theme.saveHistory(context, list)
        }
    }
}