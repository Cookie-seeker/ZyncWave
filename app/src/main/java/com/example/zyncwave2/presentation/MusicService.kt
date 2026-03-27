package com.example.zyncwave2.presentation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.zyncwave2.R
import com.example.zyncwave2.data.PlayerState

@OptIn(UnstableApi::class)
class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var lastUpdatedPath = ""

    companion object {
        const val CHANNEL_ID = "zyncwave_music"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("MusicService", "onCreate iniciando...")

        createNotificationChannel()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        PlayerState.exoPlayer = player
        android.util.Log.d("MusicService", "ExoPlayer creado y asignado: $player")

        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, buildBasicNotification())

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                android.util.Log.d("MusicService", "onMediaItemTransition: ${mediaItem?.mediaMetadata?.title}")
                mediaItem?.let { updateNotification(it) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    player.currentMediaItem?.let { updateNotification(it) }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                lastUpdatedPath = ""
                player.currentMediaItem?.let { updateNotification(it) }
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val player = PlayerState.exoPlayer ?: return START_STICKY

        android.util.Log.d("MusicService", "onStartCommand action: ${intent?.action}")

        when (intent?.action) {
            "com.example.zyncwave2.PLAY_PAUSE" -> {
                if (player.isPlaying) player.pause() else player.play()
                lastUpdatedPath = ""
                player.currentMediaItem?.let { updateNotification(it) }
            }
            "com.example.zyncwave2.NEXT" -> {
                val list = PlayerState.songsList.value
                if (list.isEmpty()) return START_STICKY
                val nextIndex = (PlayerState.currentIndex.value + 1) % list.size
                val nextSong = list.getOrNull(nextIndex) ?: return START_STICKY
                PlayerState.currentIndex.value = nextIndex
                PlayerState.currentSong.value = nextSong
                val mediaItem = MediaItem.Builder()
                    .setUri(nextSong.data)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(nextSong.title)
                            .setArtist(nextSong.artists)
                            .build()
                    )
                    .build()
                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()
            }
            "com.example.zyncwave2.PREV" -> {
                val list = PlayerState.songsList.value
                if (list.isEmpty()) return START_STICKY
                val currentIndex = PlayerState.currentIndex.value
                val prevIndex = if (currentIndex - 1 < 0) list.size - 1 else currentIndex - 1
                val prevSong = list.getOrNull(prevIndex) ?: return START_STICKY
                PlayerState.currentIndex.value = prevIndex
                PlayerState.currentSong.value = prevSong
                val mediaItem = MediaItem.Builder()
                    .setUri(prevSong.data)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(prevSong.title)
                            .setArtist(prevSong.artists)
                            .build()
                    )
                    .build()
                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ZyncWave Music",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Reproducción de música"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildBasicNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_music_note_24)
            .setContentTitle("ZyncWave")
            .setContentText("Listo para reproducir")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(mediaItem: MediaItem) {
        val path = mediaItem.localConfiguration?.uri?.path ?: return
        if (path == lastUpdatedPath) return
        lastUpdatedPath = path

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: mediaItem.mediaMetadata.title?.toString() ?: "Desconocido"
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: mediaItem.mediaMetadata.artist?.toString() ?: "Desconocido"
            val artBytes = retriever.embeddedPicture
            retriever.release()

            val artBitmap = if (artBytes != null)
                BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
            else null

            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            val contentIntent = PendingIntent.getActivity(
                this, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val prevIntent = PendingIntent.getService(
                this, 1,
                Intent(this, MusicService::class.java).apply {
                    action = "com.example.zyncwave2.PREV"
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val playPauseIntent = PendingIntent.getService(
                this, 2,
                Intent(this, MusicService::class.java).apply {
                    action = "com.example.zyncwave2.PLAY_PAUSE"
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val nextIntent = PendingIntent.getService(
                this, 3,
                Intent(this, MusicService::class.java).apply {
                    action = "com.example.zyncwave2.NEXT"
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val isPlaying = PlayerState.exoPlayer?.isPlaying ?: false

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.baseline_music_note_24)
                .setContentTitle(title)
                .setContentText(artist)
                .setLargeIcon(artBitmap)
                .setContentIntent(contentIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSilent(true)
                .setOngoing(isPlaying)
                .addAction(
                    R.drawable.outline_skip_previous_24,
                    "Anterior",
                    prevIntent
                )
                .addAction(
                    if (isPlaying) R.drawable.baseline_pause_24
                    else R.drawable.baseline_play_arrow_24,
                    if (isPlaying) "Pausar" else "Reproducir",
                    playPauseIntent
                )
                .addAction(
                    R.drawable.outline_skip_next_24,
                    "Siguiente",
                    nextIntent
                )
                .setStyle(
                    androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2)
                )
                .build()

            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, notification)

            android.util.Log.d("MusicService", "Notificación actualizada: $title - $artist")

        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Error notificación: ${e.message}", e)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null) {
            if (!player.playWhenReady || player.mediaItemCount == 0) {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}