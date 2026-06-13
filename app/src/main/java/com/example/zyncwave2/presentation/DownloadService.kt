package com.example.zyncwave2.presentation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.zyncwave2.R
import com.example.zyncwave2.data.PlayerState
import com.example.zyncwave2.data.YtDlpManager
import com.example.zyncwave2.data.getSongs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    companion object {
        const val CHANNEL_ID      = "zyncwave_download"
        const val NOTIFICATION_ID = 2001

        const val ACTION_DOWNLOAD_AUDIO  = "com.example.zyncwave2.DOWNLOAD_AUDIO"
        const val ACTION_DOWNLOAD_VIDEO  = "com.example.zyncwave2.DOWNLOAD_VIDEO"
        const val ACTION_DOWNLOAD_FORMAT = "com.example.zyncwave2.DOWNLOAD_FORMAT"
        const val ACTION_CANCEL          = "com.example.zyncwave2.CANCEL_DOWNLOAD"

        const val EXTRA_URL       = "url"
        const val EXTRA_FORMAT_ID = "format_id"
        const val EXTRA_EXT       = "ext"
        const val EXTRA_ITEM_ID   = "item_id"
        const val EXTRA_IS_AUDIO  = "is_audio"

        const val EXTRA_AUDIO_QUALITY   = "audio_quality"
        const val EXTRA_RATE_LIMIT      = "rate_limit"
        const val EXTRA_EMBED_SUBTITLES = "embed_subtitles"
        const val EXTRA_SUBTITLE_LANG   = "subtitle_lang"

        var onProgress: ((itemId: String, msg: String, progress: Float) -> Unit)? = null
        var onFinished: ((itemId: String, success: Boolean) -> Unit)? = null
        var isDownloading = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Preparando descarga...", 0f))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                downloadJob?.cancel()
                isDownloading = false
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_DOWNLOAD_AUDIO, ACTION_DOWNLOAD_VIDEO, ACTION_DOWNLOAD_FORMAT -> {
                val url    = intent.getStringExtra(EXTRA_URL)     ?: return START_NOT_STICKY
                val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return START_NOT_STICKY

                val preferences = YtDlpManager.DownloadPreferences(
                    formatId = intent.getStringExtra(EXTRA_FORMAT_ID) ?: "",
                    ext = intent.getStringExtra(EXTRA_EXT) ?: "mp3",
                    isAudio = intent.getBooleanExtra(EXTRA_IS_AUDIO, true),
                    audioQuality = YtDlpManager.AudioQuality.entries.find {
                        it.value == intent.getStringExtra(EXTRA_AUDIO_QUALITY)
                    } ?: YtDlpManager.AudioQuality.BEST,
                    rateLimit = intent.getStringExtra(EXTRA_RATE_LIMIT)
                        ?.takeIf { it.isNotBlank() },
                    embedSubtitles = intent.getBooleanExtra(EXTRA_EMBED_SUBTITLES, false),
                    subtitleLang = intent.getStringExtra(EXTRA_SUBTITLE_LANG) ?: "es,en"
                )

                isDownloading = true
                downloadJob = serviceScope.launch {
                    val success = YtDlpManager.downloadWithPreferences(
                        context     = applicationContext,
                        url         = url,
                        preferences = preferences
                    ) { msg: String, progress: Float ->
                        updateNotification(msg, progress)
                        onProgress?.invoke(itemId, msg, progress)
                    }

                    isDownloading = false
                    onFinished?.invoke(itemId, success)

                    // Re-escanear biblioteca si fue descarga de audio exitosa
                    if (success) {
                        try {
                            // Encontrar el archivo recién descargado
                            val downloadDir = android.os.Environment
                                .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                                .absolutePath + "/ZyncWave/${if (preferences.isAudio) "Audio" else "Video"}"

                            val dir = java.io.File(downloadDir)
                            val newFile = dir.listFiles()
                                ?.maxByOrNull { it.lastModified() }

                            if (newFile != null) {
                                // Escanear el archivo específico, no el directorio
                                val latch = java.util.concurrent.CountDownLatch(1)
                                android.media.MediaScannerConnection.scanFile(
                                    applicationContext,
                                    arrayOf(newFile.absolutePath),
                                    null
                                ) { _, _ -> latch.countDown() }
                                latch.await(8, java.util.concurrent.TimeUnit.SECONDS)
                            }

                            // Actualizar lista — con o sin carpetas seleccionadas
                            val folders = PlayerState.selectedFolders.value
                            val newSongs = getSongs(applicationContext, folders)
                            if (newSongs.isNotEmpty()) {
                                PlayerState.songsList.value = newSongs
                                android.util.Log.d("YTDLP", "Biblioteca actualizada: ${newSongs.size} canciones")
                            }

                        } catch (e: Exception) {
                            android.util.Log.e("YTDLP", "Error re-escaneando: ${e.message}")
                        }
                    }

                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        isDownloading = false
        super.onDestroy()
    }

    // ── Notificación ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ZyncWave Descargas",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progreso de descarga"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String, progress: Float): Notification {
        val cancelIntent = PendingIntent.getService(
            this, 0,
            Intent(this, DownloadService::class.java).apply { action = ACTION_CANCEL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val progressInt = (progress * 100).toInt()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.outline_download_24)
            .setContentTitle("ZyncWave – Descargando")
            .setContentText(text.take(60))
            .setProgress(100, progressInt, progressInt == 0)
            .addAction(R.drawable.outline_delete_24, "Cancelar", cancelIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String, progress: Float) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text, progress))
    }
}