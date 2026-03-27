package com.example.zyncwave2.presentation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.zyncwave2.R
import com.example.zyncwave2.data.YtDlpManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    companion object {
        const val CHANNEL_ID = "zyncwave_download"
        const val NOTIFICATION_ID = 2001

        const val ACTION_DOWNLOAD_AUDIO   = "com.example.zyncwave2.DOWNLOAD_AUDIO"
        const val ACTION_DOWNLOAD_VIDEO   = "com.example.zyncwave2.DOWNLOAD_VIDEO"
        const val ACTION_DOWNLOAD_FORMAT  = "com.example.zyncwave2.DOWNLOAD_FORMAT"
        const val ACTION_CANCEL           = "com.example.zyncwave2.CANCEL_DOWNLOAD"

        const val EXTRA_URL       = "url"
        const val EXTRA_FORMAT_ID = "format_id"
        const val EXTRA_EXT       = "ext"
        const val EXTRA_ITEM_ID   = "item_id"

        // Estado compartido observable desde la UI
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
                val url    = intent.getStringExtra(EXTRA_URL)    ?: return START_NOT_STICKY
                val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return START_NOT_STICKY

                isDownloading = true
                downloadJob = serviceScope.launch {
                    val success = when (intent.action) {
                        ACTION_DOWNLOAD_AUDIO -> YtDlpManager.downloadAudio(
                            applicationContext, url
                        ) { msg, progress ->
                            updateNotification(msg, progress)
                            onProgress?.invoke(itemId, msg, progress)
                        }
                        ACTION_DOWNLOAD_VIDEO -> YtDlpManager.downloadVideo(
                            applicationContext, url
                        ) { msg, progress ->
                            updateNotification(msg, progress)
                            onProgress?.invoke(itemId, msg, progress)
                        }
                        else -> {
                            val formatId = intent.getStringExtra(EXTRA_FORMAT_ID) ?: ""
                            val ext      = intent.getStringExtra(EXTRA_EXT)       ?: "mp3"
                            YtDlpManager.downloadWithFormat(
                                applicationContext, url, formatId, ext
                            ) { msg, progress ->
                                updateNotification(msg, progress)
                                onProgress?.invoke(itemId, msg, progress)
                            }
                        }
                    }
                    isDownloading = false
                    onFinished?.invoke(itemId, success)
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

    // Notificación

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
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text, progress))
    }
}