package com.example.zyncwave2.data

import android.content.Context
import android.os.Environment
import com.example.zyncwave2.presentation.App
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class VideoFormat(
    val formatId: String,
    val ext: String,
    val resolution: String,
    val filesize: String,
    val bitrate: String,
    val isAudio: Boolean,
    val note: String,
    val hasVideo: Boolean = false,
    val hasAudioTrack: Boolean = false
)

data class VideoMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val thumbnailUrl: String?
)

object YtDlpManager {

    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "webm")
    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "wav", "opus", "ogg")

    private fun getAudioDir() = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "ZyncWave/Audio"
    )

    private fun getVideoDir() = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "ZyncWave/Video"
    )

    fun isBinaryInstalled(context: Context): Boolean = App.isYtDlpReady

    fun getFfmpegPath(context: Context): String? {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val ffmpeg = File(nativeDir, "libffmpeg.so")
        return if (ffmpeg.exists()) ffmpeg.absolutePath else null
    }

    // Versión actual de yt-dlp
    suspend fun getCurrentVersion(context: Context): String = withContext(Dispatchers.IO) {
        try {
            YoutubeDL.getInstance().version(context) ?: "Desconocida"
        } catch (e: Exception) {
            "Desconocida"
        }
    }

    // Actualizar yt-dlp
    suspend fun updateYtDlp(
        context: Context,
        useNightly: Boolean = false,
        onProgress: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        try {
            onProgress("Conectando con el servidor...")
            val channel = if (useNightly)
                YoutubeDL.UpdateChannel.NIGHTLY
            else
                YoutubeDL.UpdateChannel.STABLE

            val result = YoutubeDL.getInstance().updateYoutubeDL(context, channel)

            when (result) {
                YoutubeDL.UpdateStatus.DONE -> {
                    onProgress("✓ yt-dlp actualizado correctamente")
                    "ok"
                }
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> {
                    onProgress("✓ yt-dlp ya está actualizado")
                    "uptodate"
                }
                else -> {
                    onProgress("No se pudo actualizar")
                    "error"
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("YTDLP", "Error actualizando: ${e.message}", e)
            onProgress("Error: ${e.message?.take(100)}")
            "error"
        }
    }

    // Limpiar carpeta temp antes de cada descarga
    private fun cleanTempDir(tempDir: File) {
        try {
            tempDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            android.util.Log.w("YTDLP", "No se pudo limpiar temp: ${e.message}")
        }
    }


    //Helpers

    private fun runRequest(
        request: YoutubeDLRequest,
        onOutput: ((Float, Long, String?) -> Unit)?
    ): com.yausername.youtubedl_android.YoutubeDLResponse {
        return if (onOutput != null) {
            YoutubeDL.getInstance().execute(request) { progress, eta, line ->
                onOutput(progress, eta, line)
            }
        } else {
            YoutubeDL.getInstance().execute(request)
        }
    }


    private fun executeWithSmartFallback(
        request: YoutubeDLRequest,
        onOutput: ((Float, Long, String?) -> Unit)? = null
    ): com.yausername.youtubedl_android.YoutubeDLResponse {
        return try {
            runRequest(request, onOutput)
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            val isKnownAuthFailure = msg.contains("Sign in to confirm", ignoreCase = true) ||
                    msg.contains("403", ignoreCase = true)

            if (isKnownAuthFailure) {
                android.util.Log.w("YTDLP", "Fallo con cliente default, reintentando con android_vr forzado")
                request.addOption("--extractor-args", "youtube:player_client=android_vr")
                runRequest(request, onOutput)
            } else {
                throw e
            }
        }
    }

    suspend fun getVideoTitle(
        context: Context,
        url: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--get-title")
                addOption("--no-playlist")

            }
            val response = executeWithSmartFallback(request)
            response.out.trim().lines().firstOrNull()
        } catch (e: Exception) {
            android.util.Log.e("YTDLP", "Error obteniendo título: ${e.message}")
            null
        }
    }

    suspend fun getMetadata(
        context: Context,
        url: String
    ): VideoMetadata? = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--print", "%(title)s|%(uploader)s|%(album)s|%(thumbnail)s")
                addOption("--no-playlist")

            }
            val response = executeWithSmartFallback(request)
            val parts = response.out.trim().split("|")
            VideoMetadata(
                title        = parts.getOrNull(0)?.trim() ?: "",
                artist       = parts.getOrNull(1)?.trim() ?: "",
                album        = parts.getOrNull(2)?.trim() ?: "",
                thumbnailUrl = parts.getOrNull(3)?.trim()
            )
        } catch (e: Exception) {
            android.util.Log.e("YTDLP", "Error obteniendo metadata: ${e.message}")
            null
        }
    }

    suspend fun getFormats(
        context: Context,
        url: String
    ): Pair<List<VideoFormat>, String?> = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("-F")
                addOption("--no-playlist")
            }
            val response = executeWithSmartFallback(request)

            android.util.Log.d("YTDLP_RAW", "==== RAW -F OUTPUT ====\n${response.out}")

            Pair(parseFormats(response.out), null)
        } catch (e: Exception) {
            android.util.Log.e("YTDLP", "Error obteniendo formatos: ${e.message}")
            val rawError = e.message ?: "Error desconocido"
            Pair(emptyList(), rawError)
        }
    }

    fun translateError(rawError: String): String {
        val lower = rawError.lowercase()
        return when {
            "429" in lower || "too many requests" in lower ->
                "El sitio está limitando las solicitudes por exceso de tráfico. Esperá unos minutos e intentá de nuevo."

            "sign in to confirm" in lower || "not a bot" in lower ->
                "El sitio requiere verificar que no sos un bot. Puede pasar por uso repetido; probá más tarde o desde otra red."

            "impersonation" in lower ->
                "El sitio cambió su estructura y el extractor no pudo simular correctamente el acceso. Puede necesitar una actualización de yt-dlp."

            "unsupported url" in lower ->
                "Este enlace no corresponde a un sitio compatible."

            "video unavailable" in lower ->
                "El video no está disponible (fue eliminado, es privado, o está restringido en tu región)."

            "private video" in lower ->
                "Este video es privado y no se puede descargar."

            "js runtime" in lower || "javascript runtime" in lower ->
                "Falta un componente interno (runtime de JavaScript) necesario para procesar este enlace."

            "no title found" in lower ->
                "No se pudo obtener información del video, pero puede que igual funcione la descarga."

            else ->
                "No se pudo procesar el enlace. Revisá el detalle técnico abajo para más información."
        }
    }

    private fun parseFormats(output: String): List<VideoFormat> {
        val formats = mutableListOf<VideoFormat>()
        val lines = output.lines()
        var inFormats = false

        for (line in lines) {
            if (line.contains("ID") && line.contains("EXT") && line.contains("RESOLUTION")) {
                inFormats = true
                continue
            }
            if (!inFormats) continue
            if (line.isBlank() || line.startsWith("---")) continue

            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 3) continue

            if (parts.getOrNull(1)?.lowercase() == "mhtml") continue

            try {
                val formatId   = parts[0]
                val ext        = parts[1]
                val resolution = parts[2]
                val lineLC     = line.lowercase()

                val isAudioOnly   = resolution == "audio" || lineLC.contains("audio only")
                val hasVideo      = !isAudioOnly
                val hasAudioTrack = hasVideo && !lineLC.contains("video only")

                val filesize = parts.firstOrNull { it.contains("MiB") || it.contains("KiB") } ?: ""
                val bitrate  = parts.firstOrNull { it.endsWith("k") && it.dropLast(1).toDoubleOrNull() != null } ?: ""
                val note     = parts.drop(3).joinToString(" ").take(40)

                formats.add(
                    VideoFormat(
                        formatId      = formatId,
                        ext           = ext,
                        resolution    = resolution,
                        filesize      = filesize,
                        bitrate       = bitrate,
                        isAudio       = isAudioOnly,
                        note          = note,
                        hasVideo      = hasVideo,
                        hasAudioTrack = hasAudioTrack
                    )
                )
            } catch (e: Exception) { continue }
        }
        return formats
    }

    // Guardar archivo con copia directa
    private fun saveFileDirect(
        context: Context,
        file: File,
        isVideoIntent: Boolean
    ) {
        val ext = file.extension.lowercase()

        if (ext !in AUDIO_EXTENSIONS && ext !in VIDEO_EXTENSIONS) {
            android.util.Log.d("YTDLP", "Ignorando archivo no multimedia: ${file.name}")
            file.delete()
            return
        }

        val actuallyVideo = when (ext) {
            "webm" -> isVideoIntent
            else   -> ext in VIDEO_EXTENSIONS
        }

        val destDir = if (actuallyVideo) getVideoDir() else getAudioDir()
        if (!destDir.exists()) destDir.mkdirs()

        val dest = File(destDir, file.name)
        file.copyTo(dest, overwrite = true)
        file.delete()

        android.media.MediaScannerConnection.scanFile(
            context, arrayOf(dest.absolutePath), null, null
        )
        android.util.Log.d("YTDLP", "Guardado directo: ${dest.absolutePath}")
    }

    suspend fun downloadAudio(
        context: Context,
        url: String,
        onProgress: (String, Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempDir = File(context.cacheDir, "ytdlp_temp")
            if (!tempDir.exists()) tempDir.mkdirs()
            cleanTempDir(tempDir)
            onProgress("Iniciando descarga de audio...", 0f)

            val request = YoutubeDLRequest(url).apply {
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", "0")
                addOption("--add-metadata")
                addOption("--embed-thumbnail")
                addOption("-o", "${tempDir.absolutePath}/%(title).50s.%(ext)s")
                addOption("--no-playlist")

                addOption("--no-warnings")
            }

            YoutubeDL.getInstance().execute(request) { progress, _, line ->
                onProgress(line ?: "$progress%", progress / 100f)
            }

            onProgress("Guardando en ZyncWave/Audio...", 0.95f)
            tempDir.listFiles()?.forEach { saveFileDirect(context, it, isVideoIntent = false) }
            onProgress("✓ Audio guardado en ZyncWave/Audio", 1f)
            true
        } catch (e: Exception) {
            android.util.Log.e("YTDLP", "ERROR:", e)
            onProgress("Error: ${e.message?.take(150)}", 0f)
            false
        }
    }

    suspend fun downloadVideo(
        context: Context,
        url: String,
        onProgress: (String, Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempDir = File(context.cacheDir, "ytdlp_temp")
            if (!tempDir.exists()) tempDir.mkdirs()
            cleanTempDir(tempDir)
            onProgress("Iniciando descarga de video...", 0f)

            val request = YoutubeDLRequest(url).apply {
                addOption("-f", "best[ext=mp4]/best")
                addOption("--add-metadata")
                addOption("--embed-thumbnail")
                addOption("-o", "${tempDir.absolutePath}/%(title).50s.%(ext)s")
                addOption("--no-playlist")

                addOption("--no-warnings")
            }

            YoutubeDL.getInstance().execute(request) { progress, _, line ->
                onProgress(line ?: "$progress%", progress / 100f)
            }

            onProgress("Guardando en ZyncWave/Video...", 0.95f)
            tempDir.listFiles()?.forEach { saveFileDirect(context, it, isVideoIntent = true) }
            onProgress("✓ Video guardado en ZyncWave/Video", 1f)
            true
        } catch (e: Exception) {
            android.util.Log.e("YTDLP", "ERROR:", e)
            onProgress("Error: ${e.message?.take(150)}", 0f)
            false
        }
    }

    suspend fun downloadWithFormat(
        context: Context,
        url: String,
        formatId: String,
        ext: String,
        isAudioFormat: Boolean,
        onProgress: (String, Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempDir = File(context.cacheDir, "ytdlp_temp")
            if (!tempDir.exists()) tempDir.mkdirs()
            cleanTempDir(tempDir)

            android.util.Log.d("YTDLP", "Descargando formato: $formatId ext: $ext isAudio: $isAudioFormat")
            onProgress("Iniciando descarga...", 0f)

            val isVideoIntent    = !isAudioFormat
            val nativeAudioExts  = setOf("opus", "ogg")
            val convertAudioExts = setOf("mp3", "m4a", "aac", "flac", "wav")
            val thumbnailSupportedExts = setOf("mp3", "m4a", "mp4", "mkv", "ogg", "opus", "flac", "mov")

            val finalExt = when {
                ext == "webm" && !isVideoIntent -> "opus"
                else -> ext
            }

            val request = YoutubeDLRequest(url).apply {
                addOption("-f", formatId)
                when {
                    ext == "webm" && !isVideoIntent -> {
                        addOption("-x")
                        addOption("--audio-format", "opus")
                        addOption("--audio-quality", "0")
                    }
                    ext in nativeAudioExts && !isVideoIntent -> Unit
                    ext in convertAudioExts && !isVideoIntent -> {
                        addOption("-x")
                        addOption("--audio-format", ext)
                        addOption("--audio-quality", "0")
                    }
                }
                addOption("--add-metadata")
                if (finalExt in thumbnailSupportedExts) {
                    addOption("--embed-thumbnail")
                }
                addOption("-o", "${tempDir.absolutePath}/%(title).50s.%(ext)s")
                addOption("--no-playlist")

                addOption("--no-warnings")
            }

            YoutubeDL.getInstance().execute(request) { progress, _, line ->
                android.util.Log.d("YTDLP", "Progress: $progress - $line")
                onProgress(line ?: "$progress%", progress / 100f)
            }

            val files = tempDir.listFiles()
            android.util.Log.d("YTDLP", "Archivos en temp: ${files?.map { it.name }}")
            files?.forEach { saveFileDirect(context, it, isVideoIntent) }

            onProgress("✓ Guardado en ZyncWave/${if (isVideoIntent) "Video" else "Audio"}", 1f)
            true
        } catch (e: Exception) {
            android.util.Log.e("YTDLP", "ERROR downloadWithFormat: ${e.message}", e)
            onProgress("Error: ${e.message?.take(150)}", 0f)
            false
        }
    }

    suspend fun downloadWithPreferences(
        context: Context,
        url: String,
        preferences: DownloadPreferences,
        onProgress: (String, Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempDir = File(context.cacheDir, "ytdlp_temp")
            if (!tempDir.exists()) tempDir.mkdirs()
            cleanTempDir(tempDir)

            android.util.Log.d("YTDLP", "Descargando con preferencias: formatId=${preferences.formatId} ext=${preferences.ext} isAudio=${preferences.isAudio}")
            onProgress("Iniciando descarga...", 0f)

            val isVideoIntent    = !preferences.isAudio
            val nativeAudioExts  = setOf("opus", "ogg")
            val convertAudioExts = setOf("mp3", "m4a", "aac", "flac", "wav")
            val thumbnailSupportedExts = setOf("mp3", "m4a", "mp4", "mkv", "ogg", "opus", "flac", "mov")

            val finalExt = when {
                preferences.ext == "webm" && !isVideoIntent -> "opus"
                else -> preferences.ext
            }

            val request = YoutubeDLRequest(url).apply {
                addOption("-f", preferences.formatId)
                when {
                    preferences.ext == "webm" && !isVideoIntent -> {
                        addOption("-x")
                        addOption("--audio-format", "opus")
                    }
                    preferences.ext in nativeAudioExts && !isVideoIntent -> Unit
                    preferences.ext in convertAudioExts && !isVideoIntent -> {
                        addOption("-x")
                        addOption("--audio-format", preferences.ext)
                    }
                }
                addOption("--add-metadata")
                if (finalExt in thumbnailSupportedExts) {
                    addOption("--embed-thumbnail")
                }
                addOption("-o", "${tempDir.absolutePath}/%(title).50s.%(ext)s")
                addOption("--no-playlist")

                addOption("--no-warnings")
            }

            preferences.applyTo(request)

            YoutubeDL.getInstance().execute(request) { progress, _, line ->
                android.util.Log.d("YTDLP", "Progress: $progress - $line")
                onProgress(line ?: "$progress%", progress / 100f)
            }

            val files = tempDir.listFiles()
            android.util.Log.d("YTDLP", "Archivos en temp: ${files?.map { it.name }}")
            files?.forEach { saveFileDirect(context, it, isVideoIntent) }

            onProgress("✓ Guardado en ZyncWave/${if (isVideoIntent) "Video" else "Audio"}", 1f)
            true
        } catch (e: Exception) {
            android.util.Log.e("YTDLP", "ERROR downloadWithPreferences: ${e.message}", e)
            onProgress("Error: ${e.message?.take(150)}", 0f)
            false
        }
    }

    data class DownloadPreferences(
        val audioQuality: AudioQuality = AudioQuality.BEST,
        val rateLimit: String? = null,
        val embedSubtitles: Boolean = false,
        val subtitleLang: String = "es,en",
        val formatId: String = "",
        val ext: String = "",
        val isAudio: Boolean = true
    ) {
        fun applyTo(request: com.yausername.youtubedl_android.YoutubeDLRequest) {
            if (isAudio) {
                request.addOption("--audio-quality", audioQuality.value)
            }
            rateLimit?.takeIf { it.isNotBlank() }?.let {
                request.addOption("--rate-limit", it)
            }
            if (!isAudio && embedSubtitles) {
                request.addOption("--embed-subs")
                request.addOption("--sub-langs", subtitleLang)
                request.addOption("--write-auto-subs")
            }
        }
    }

    enum class AudioQuality(val value: String, val label: String) {
        BEST("0",    "Mejor calidad"),
        HIGH("128K", "128 Kbps"),
        MID("192K",  "192 Kbps"),
        LOW("320K",  "320 Kbps")
    }
}