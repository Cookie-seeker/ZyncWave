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
    private var mediaSessionCompat: android.support.v4.media.session.MediaSessionCompat? = null

    private var cachedTitle = ""
    private var cachedArtist = ""
    private var cachedArtBitmap: android.graphics.Bitmap? = null
    private var cachedIsPlaying = false

    // Receiver interno para UPDATE_NOTIF
    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateNotificationFast()
        }
    }

    companion object {
        const val CHANNEL_ID      = "zyncwave_music_v3"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()

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
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        PlayerState.exoPlayer = player

        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val progressRunnable = object : Runnable {
            override fun run() {
                val p = PlayerState.exoPlayer ?: return
                if (p.isPlaying) updatePlaybackState(p)
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(progressRunnable)

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()

        mediaSessionCompat = android.support.v4.media.session.MediaSessionCompat(
            this, "ZyncWaveSession"
        ).also { compat ->
            compat.setCallback(object : android.support.v4.media.session.MediaSessionCompat.Callback() {
                override fun onPlay() {
                    PlayerState.exoPlayer?.play()
                }
                override fun onPause() {
                    PlayerState.exoPlayer?.pause()
                }
                override fun onSkipToNext() {
                    PlayerState.exoPlayer?.seekToNextMediaItem()
                }
                override fun onSkipToPrevious() {
                    PlayerState.exoPlayer?.seekToPreviousMediaItem()
                }
                override fun onSeekTo(pos: Long) {
                    PlayerState.exoPlayer?.seekTo(pos)
                }
                override fun onStop() {
                    PlayerState.exoPlayer?.pause()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            })
            compat.isActive = true
        }

        // Arrancar foreground inmediatamente con notificación básica
        startForeground(NOTIFICATION_ID, buildBasicNotification())

        // Registrar receiver para actualizar notificación cuando cambia favorito
        val filter = IntentFilter(NotificationReceiver.ACTION_UPDATE_NOTIF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(updateReceiver, filter)
        }

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                android.util.Log.d("MusicService", "onMediaItemTransition: ${mediaItem?.mediaMetadata?.title}")
                lastUpdatedPath = ""
                mediaItem?.let { updateNotification(it) }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                android.util.Log.d("MusicService", "onPlaybackStateChanged: $playbackState")
                if (playbackState == Player.STATE_READY) {
                    lastUpdatedPath = ""
                    player.currentMediaItem?.let { updateNotification(it) }
                }

                updatePlaybackState(player)

            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                android.util.Log.d("MusicService", "onIsPlayingChanged: $isPlaying")
                lastUpdatedPath = ""
                player.currentMediaItem?.let { updateNotification(it) }

                updatePlaybackState(player)

            }
        })

        val session = PlayerState.loadLastSession(this)
        if (session != null) {
            val (_, positionMs) = session
            PlayerState.lastRestoredPosition = positionMs
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == "com.example.zyncwave2.CLOSE") {
            PlayerState.exoPlayer?.pause()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ZyncWave Music",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildBasicNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_music_note_24)
            .setContentTitle("ZyncWave")
            .setContentText("Listo para reproducir")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun broadcast(action: String, reqCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            this, reqCode,
            Intent(action).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun updateNotification(mediaItem: MediaItem) {
        val path = mediaItem.localConfiguration?.uri?.path ?: return

        // ← Leer del player en el main thread ANTES de entrar al Thread
        val isPlaying  = PlayerState.exoPlayer?.isPlaying ?: false
        val isFavorite = com.example.zyncwave2.data.FavoritesManager
            .isFavorite(PlayerState.currentSong.value?.id ?: 0L)

        Thread {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(path)
                val title  = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?: mediaItem.mediaMetadata.title?.toString() ?: "Desconocido"
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: mediaItem.mediaMetadata.artist?.toString() ?: "Desconocido"
                val artBytes = retriever.embeddedPicture

                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L

                retriever.release()

                val artBitmap = artBytes?.let {
                    BitmapFactory.decodeByteArray(it, 0, it.size)
                }

                cachedTitle = title
                cachedArtist = artist
                cachedArtBitmap = artBitmap


                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                val contentPI = PendingIntent.getActivity(
                    this, 0, launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val metadata = android.support.v4.media.MediaMetadataCompat.Builder()
                    .putString(
                        android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, title
                    )
                    .putString(
                        android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, artist
                    )
                    .putBitmap(
                        android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artBitmap
                    )
                    .putLong(
                        android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION, durationMs
                    )
                    .build()

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    mediaSessionCompat?.setMetadata(metadata)
                }

                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.baseline_music_note_24)
                    .setContentTitle(title)
                    .setContentText(artist)
                    .setLargeIcon(artBitmap)
                    .setContentIntent(contentPI)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOngoing(isPlaying)
                    .addAction(
                        if (isFavorite) R.drawable.baseline_favorite_24
                        else R.drawable.outline_favorite_24,
                        "Favorito",
                        broadcast(NotificationReceiver.ACTION_FAVORITE, 4)
                    )
                    .addAction(
                        R.drawable.outline_skip_previous_24, "Anterior",
                        broadcast(NotificationReceiver.ACTION_PREV, 1)
                    )
                    .addAction(
                        if (isPlaying) R.drawable.baseline_pause_24
                        else R.drawable.baseline_play_arrow_24,
                        if (isPlaying) "Pausar" else "Reproducir",
                        broadcast(NotificationReceiver.ACTION_PLAY_PAUSE, 2)
                    )
                    .addAction(
                        R.drawable.outline_skip_next_24, "Siguiente",
                        broadcast(NotificationReceiver.ACTION_NEXT, 3)
                    )
                    .addAction(
                        R.drawable.outline_close_24, "Cerrar",
                        broadcast(NotificationReceiver.ACTION_CLOSE, 5)
                    )
                    .setStyle(
                        androidx.media.app.NotificationCompat.MediaStyle()
                            .setMediaSession(mediaSessionCompat?.sessionToken)
                            .setShowActionsInCompactView(0, 1, 2, 3, 4)
                    )
                    .build()

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    startForeground(NOTIFICATION_ID, notification)
                }

            } catch (e: Exception) {
                android.util.Log.e("MusicService", "Error notif: ${e.message}")
            }
        }.start()

    }


    private fun updateNotificationFast() {
        if (cachedTitle.isEmpty()) return  // aún no hay caché

        val isPlaying  = PlayerState.exoPlayer?.isPlaying ?: false
        val isFavorite = com.example.zyncwave2.data.FavoritesManager
            .isFavorite(PlayerState.currentSong.value?.id ?: 0L)

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPI = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_music_note_24)
            .setContentTitle(cachedTitle)
            .setContentText(cachedArtist)
            .setLargeIcon(cachedArtBitmap)
            .setContentIntent(contentPI)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(
                if (isFavorite) R.drawable.baseline_favorite_24
                else R.drawable.outline_favorite_24,
                "Favorito",
                broadcast(NotificationReceiver.ACTION_FAVORITE, 4)
            )
            .addAction(
                R.drawable.outline_skip_previous_24, "Anterior",
                broadcast(NotificationReceiver.ACTION_PREV, 1)
            )
            .addAction(
                if (isPlaying) R.drawable.baseline_pause_24
                else R.drawable.baseline_play_arrow_24,
                if (isPlaying) "Pausar" else "Reproducir",
                broadcast(NotificationReceiver.ACTION_PLAY_PAUSE, 2)
            )
            .addAction(
                R.drawable.outline_skip_next_24, "Siguiente",
                broadcast(NotificationReceiver.ACTION_NEXT, 3)
            )
            .addAction(
                R.drawable.outline_close_24, "Cerrar",
                broadcast(NotificationReceiver.ACTION_CLOSE, 5)
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSessionCompat?.sessionToken)
                    .setShowActionsInCompactView(1, 2, 3)
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updatePlaybackState(player: ExoPlayer) {
        android.util.Log.d("MusicService", "updatePlaybackState: isPlaying=${player.isPlaying} pos=${player.currentPosition}")
        val state = if (player.isPlaying)
            android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING
        else
            android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED

        val playbackState = android.support.v4.media.session.PlaybackStateCompat.Builder()
            .setState(
                state,
                player.currentPosition,
                player.playbackParameters.speed
            )
            .setActions(
                android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY or
                        android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE or
                        android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        android.support.v4.media.session.PlaybackStateCompat.ACTION_SEEK_TO
            )
            .build()

        mediaSessionCompat?.setPlaybackState(playbackState)
        android.util.Log.d("MusicService", "PlaybackState seteado: $state pos=${player.currentPosition}")

    }


    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && (!player.playWhenReady || player.mediaItemCount == 0)) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        unregisterReceiver(updateReceiver)
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        mediaSessionCompat?.release()
        super.onDestroy()

    }
}