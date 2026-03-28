package com.example.zyncwave2.data

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
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
    val note: String
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
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
        "ZyncWave/Audio"
    )

    private fun getVideoDir() = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
        "ZyncWave/Video"
    )

    fun isBinaryInstalled(context: Context): Boolean {
        return App.isYtDlpReady
    }

    fun getFfmpegPath(context: Context): String? {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val ffmpeg = File(nativeDir, "libffmpeg.so")
        return if (ffmpeg.exists()) ffmpeg.absolutePath else null
    }

    //  FIX: Limpiar carpeta temp antes de cada descarga
    private fun cleanTempDir(tempDir: File) {
        try {
            tempDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            android.util.Log.w("YTDLP", "No se pudo limpiar temp: ${e.message}")
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
            val response = YoutubeDL.getInstance().execute(request)
            response.out.trim().lines().firstOrNull()
        } catch (e: Exception) {
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
            val response = YoutubeDL.getInstance().execute(request)
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
    ): List<VideoFormat> = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("-F")
                addOption("--no-playlist")
            }
            val response = YoutubeDL.getInstance().execute(request)
            parseFormats(response.out)
        } catch (e: Exception) {
            android.util.Log.e("YTDLP", "Error obteniendo formatos: ${e.message}")
            emptyList()
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

            try {
                val formatId   = parts[0]
                val ext        = parts[1]
                val resolution = parts[2]
                val isAudio    = resolution == "audio" || line.contains("audio only")
                val filesize   = parts.firstOrNull { it.contains("MiB") || it.contains("KiB") } ?: ""
                val bitrate    = parts.firstOrNull { it.endsWith("k") && it.dropLast(1).toDoubleOrNull() != null } ?: ""
                val note       = parts.drop(3).joinToString(" ").take(40)
                formats.add(VideoFormat(formatId, ext, resolution, filesize, bitrate, isAudio, note))
            } catch (e: Exception) {
                continue
            }
        }
        return formats
    }

    // Detección correcta de video/audio por extensión real del archivo
    private fun saveFileViaMediaStore(
        context: Context,
        file: File,
        isVideoIntent: Boolean   // lo que el usuario pidió (audio o video)
    ) {
        val ext = file.extension.lowercase()

        // Ignorar miniaturas y archivos temporales que no son audio/video
        if (ext !in AUDIO_EXTENSIONS && ext !in VIDEO_EXTENSIONS) {
            android.util.Log.d("YTDLP", "Ignorando archivo no multimedia: ${file.name}")
            file.delete()
            return
        }

        // La extensión real del archivo manda, no lo que el usuario pidió
        val actuallyVideo = ext in VIDEO_EXTENSIONS

        android.util.Log.d("YTDLP", "Guardando: ${file.name}, ext: $ext, isVideo: $actuallyVideo")

        // webm/opus/ogg: copiar directo sin MediaStore para evitar conflictos de MIME
        if (ext in setOf("opus", "ogg")) {
            val destDir = getAudioDir()
            if (!destDir.exists()) destDir.mkdirs()
            val dest = File(destDir, file.name)
            file.copyTo(dest, overwrite = true)
            file.delete()
            android.media.MediaScannerConnection.scanFile(
                context, arrayOf(dest.absolutePath), null, null
            )
            android.util.Log.d("YTDLP", "Guardado directo: ${dest.absolutePath}")
            return
        }

        val mimeType = when (ext) {
            "mp3"  -> "audio/mpeg"
            "m4a"  -> "audio/mp4"
            "aac"  -> "audio/aac"
            "flac" -> "audio/flac"
            "wav"  -> "audio/wav"
            "mp4"  -> "video/mp4"
            "mkv"  -> "video/x-matroska"
            "avi"  -> "video/avi"
            "mov"  -> "video/quicktime"
            "webm" -> if (actuallyVideo) "video/webm" else "audio/webm"
            else   -> if (actuallyVideo) "video/mp4" else "audio/mpeg"
        }

        val relativePath = if (actuallyVideo) "Music/ZyncWave/Video" else "Music/ZyncWave/Audio"

        val collection = if (actuallyVideo)
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }

        val uri = context.contentResolver.insert(collection, values)
        android.util.Log.d("YTDLP", "URI resultado: $uri")

        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { out ->
                file.inputStream().use { inp -> inp.copyTo(out) }
            }
            android.util.Log.d("YTDLP", "Guardado MediaStore: ${file.name} -> $relativePath")
        }
        file.delete()
    }

    suspend fun downloadAudio(
        context: Context,
        url: String,
        onProgress: (String, Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempDir = File(context.cacheDir, "ytdlp_temp")
            if (!tempDir.exists()) tempDir.mkdirs()

            // ── FIX: limpiar temp antes de descargar ──
            cleanTempDir(tempDir)

            onProgress("Iniciando descarga de audio...", 0f)

            val request = YoutubeDLRequest(url).apply {
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", "0")
                addOption("--add-metadata")
                addOption("--embed-thumbnail")
                addOption("-o", "${tempDir.absolutePath}/%(title)s.%(ext)s")
                addOption("--no-playlist")
                addOption("--no-warnings")
            }

            YoutubeDL.getInstance().execute(request) { progress, _, line ->
                onProgress(line ?: "$progress%", progress / 100f)
            }

            onProgress("Guardando en ZyncWave/Audio...", 0.95f)

            tempDir.listFiles()?.forEach { file ->
                saveFileViaMediaStore(context, file, isVideoIntent = false)
            }

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

            // ── FIX: limpiar temp antes de descargar ──
            cleanTempDir(tempDir)

            onProgress("Iniciando descarga de video...", 0f)

            val request = YoutubeDLRequest(url).apply {
                addOption("-f", "best[ext=mp4]/best")
                addOption("--add-metadata")
                addOption("--embed-thumbnail")
                addOption("-o", "${tempDir.absolutePath}/%(title)s.%(ext)s")
                addOption("--no-playlist")
                addOption("--no-warnings")
            }

            YoutubeDL.getInstance().execute(request) { progress, _, line ->
                onProgress(line ?: "$progress%", progress / 100f)
            }

            onProgress("Guardando en ZyncWave/Video...", 0.95f)

            tempDir.listFiles()?.forEach { file ->
                saveFileViaMediaStore(context, file, isVideoIntent = true)
            }

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
        onProgress: (String, Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempDir = File(context.cacheDir, "ytdlp_temp")
            if (!tempDir.exists()) tempDir.mkdirs()

            // ── FIX: limpiar temp antes de descargar ──
            cleanTempDir(tempDir)

            android.util.Log.d("YTDLP", "Descargando formato: $formatId ext: $ext")
            onProgress("Iniciando descarga...", 0f)

            val isVideoIntent  = ext in VIDEO_EXTENSIONS
            val nativeAudioExts   = setOf("opus", "ogg")
            val convertAudioExts  = setOf("mp3", "m4a", "aac", "flac", "wav")

            val request = YoutubeDLRequest(url).apply {
                addOption("-f", formatId)
                when {
                    ext in nativeAudioExts -> {
                        android.util.Log.d("YTDLP", "Descargando nativo: $ext")
                    }
                    ext == "webm" && !isVideoIntent -> {
                        addOption("-x")
                        addOption("--audio-format", "mp3")
                        addOption("--audio-quality", "0")
                        android.util.Log.d("YTDLP", "Convirtiendo webm a mp3")
                    }
                    ext in convertAudioExts -> {
                        addOption("-x")
                        addOption("--audio-format", ext)
                        addOption("--audio-quality", "0")
                    }
                }
                addOption("--add-metadata")
                addOption("--embed-thumbnail")
                addOption("-o", "${tempDir.absolutePath}/%(title)s.%(ext)s")
                addOption("--no-playlist")
                addOption("--no-warnings")
            }

            YoutubeDL.getInstance().execute(request) { progress, _, line ->
                android.util.Log.d("YTDLP", "Progress: $progress - $line")
                onProgress(line ?: "$progress%", progress / 100f)
            }

            val files = tempDir.listFiles()
            android.util.Log.d("YTDLP", "Archivos en temp: ${files?.map { it.name }}")

            files?.forEach { file ->
                saveFileViaMediaStore(context, file, isVideoIntent)
            }

            onProgress("✓ Guardado en ZyncWave/${if (isVideoIntent) "Video" else "Audio"}", 1f)
            true
        } catch (e: Exception) {
            android.util.Log.e("YTDLP", "ERROR downloadWithFormat: ${e.message}", e)
            onProgress("Error: ${e.message?.take(150)}", 0f)
            false
        }
    }
}