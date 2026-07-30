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
import com.example.zyncwave2.data.FavoritesManager
import com.example.zyncwave2.data.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var mediaSessionCompat: android.support.v4.media.session.MediaSessionCompat? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Job de la notificación activa
    private var notificationJob: Job? = null

    // Caché en memoria para updateNotificationFast() — evita releer disco
    private var cachedTitle      = ""
    private var cachedArtist     = ""
    private var cachedArtBitmap: android.graphics.Bitmap? = null

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateNotificationFast()
        }
    }

    companion object {
        const val CHANNEL_ID      = "zyncwave_music_v3"
        const val NOTIFICATION_ID = 1001
    }

    //Ciclo

    override fun onCreate() {
        android.util.Log.d("PERF", "MusicService.onCreate START: ${System.currentTimeMillis()}")
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

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()

        mediaSessionCompat = android.support.v4.media.session.MediaSessionCompat(
            this, "ZyncWaveSession"
        ).also { compat ->
            compat.setCallback(object :
                android.support.v4.media.session.MediaSessionCompat.Callback() {
                override fun onPlay()             { PlayerState.exoPlayer?.play() }
                override fun onPause()            { PlayerState.exoPlayer?.pause() }
                override fun onSkipToNext()       { PlayerState.exoPlayer?.seekToNextMediaItem() }
                override fun onSkipToPrevious()   { PlayerState.exoPlayer?.seekToPreviousMediaItem() }
                override fun onSeekTo(pos: Long)  { PlayerState.exoPlayer?.seekTo(pos) }
                override fun onStop() {
                    PlayerState.exoPlayer?.pause()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            })
            compat.isActive = true
        }

        startForeground(NOTIFICATION_ID, buildBasicNotification())
        android.util.Log.d("PERF", "MusicService.onCreate END: ${System.currentTimeMillis()}")

        val filter = IntentFilter(NotificationReceiver.ACTION_UPDATE_NOTIF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(updateReceiver, filter)
        }

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.let { updateNotification(it) }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    player.currentMediaItem?.let { updateNotification(it) }
                }
                updatePlaybackState(player)
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                player.currentMediaItem?.let { updateNotification(it) }
                updatePlaybackState(player)
            }
        })

        // Ticker de posición para MediaSession — reemplaza el Handler/Runnable anterior.
        // Corre cada segundo mientras el servicio está vivo; se cancela con serviceScope.
        serviceScope.launch {
            while (isActive) {
                delay(1000)
                val p = PlayerState.exoPlayer ?: continue
                if (p.isPlaying) updatePlaybackState(p)
            }
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && (!player.playWhenReady || player.mediaItemCount == 0)) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        unregisterReceiver(updateReceiver)
        // Cancela todas las coroutines pendientes (notificationJob incluido)
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        mediaSessionCompat?.release()
        super.onDestroy()
    }

    //Notificaciones

    /**
     * Actualización completa: lee metadatos del disco (IO) y construye la
     * notificación (Main). Cancela cualquier update previo antes de arrancar,
     * así solo hay un Job activo a la vez — sin race conditions.
     */
    private fun updateNotification(mediaItem: MediaItem) {
        val path = mediaItem.localConfiguration?.uri?.path ?: return

        // Leer isPlaying e isFavorite en Main antes de entrar al IO
        val isPlaying  = PlayerState.exoPlayer?.isPlaying ?: false
        val isFavorite = FavoritesManager.isFavorite(PlayerState.currentSong.value?.id ?: 0L)

        // Cancelar el update anterior — si cambiamos de canción rápido,
        // no queremos que la notificación de la canción vieja llegue después
        notificationJob?.cancel()
        notificationJob = serviceScope.launch {

            //Trabajo de disco en IO
            data class Meta(
                val title: String,
                val artist: String,
                val bitmap: android.graphics.Bitmap?,
                val durationMs: Long
            )

            val meta = withContext(Dispatchers.IO) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(path)
                    val title = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_TITLE
                    ) ?: mediaItem.mediaMetadata.title?.toString() ?: "Desconocido"
                    val artist = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_ARTIST
                    ) ?: mediaItem.mediaMetadata.artist?.toString() ?: "Desconocido"
                    val artBytes  = retriever.embeddedPicture
                    val durationMs = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION
                    )?.toLongOrNull() ?: 0L
                    retriever.release()

                    Meta(
                        title      = title,
                        artist     = artist,
                        bitmap     = artBytes?.let {
                            BitmapFactory.decodeByteArray(it, 0, it.size)
                        },
                        durationMs = durationMs
                    )
                } catch (e: Exception) {
                    android.util.Log.e("MusicService", "Error leyendo metadata: ${e.message}")
                    null
                }
            } ?: return@launch

            //De vuelta en Main — actualizar caché y notificación
            cachedTitle     = meta.title
            cachedArtist    = meta.artist
            cachedArtBitmap = meta.bitmap

            // Actualizar MediaSessionCompat metadata (para lockscreen / wearables)
            mediaSessionCompat?.setMetadata(
                android.support.v4.media.MediaMetadataCompat.Builder()
                    .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE,     meta.title)
                    .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST,    meta.artist)
                    .putBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART, meta.bitmap)
                    .putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION,    meta.durationMs)
                    .build()
            )

            startForeground(NOTIFICATION_ID, buildNotification(
                title      = meta.title,
                artist     = meta.artist,
                bitmap     = meta.bitmap,
                isPlaying  = isPlaying,
                isFavorite = isFavorite
            ))
        }
    }

    /**
     * Actualización rápida: usa la caché, sin IO.
     * Llamada desde ACTION_UPDATE_NOTIF (cambio de favorito).
     */
    private fun updateNotificationFast() {
        if (cachedTitle.isEmpty()) return
        val isPlaying  = PlayerState.exoPlayer?.isPlaying ?: false
        val isFavorite = FavoritesManager.isFavorite(PlayerState.currentSong.value?.id ?: 0L)
        startForeground(NOTIFICATION_ID, buildNotification(
            title      = cachedTitle,
            artist     = cachedArtist,
            bitmap     = cachedArtBitmap,
            isPlaying  = isPlaying,
            isFavorite = isFavorite
        ))
    }

    /** Construye la notificación completa a partir de datos ya resueltos. */
    private fun buildNotification(
        title: String,
        artist: String,
        bitmap: android.graphics.Bitmap?,
        isPlaying: Boolean,
        isFavorite: Boolean
    ): Notification {
        val contentPI = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_music_note_24)
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(bitmap)
            .setContentIntent(contentPI)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(
                if (isFavorite) R.drawable.baseline_favorite_24
                else R.drawable.outline_favorite_24,
                "Favorito",
                broadcast(NotificationReceiver.ACTION_FAVORITE, 4)
            )
            .addAction(R.drawable.outline_skip_previous_24, "Anterior",
                broadcast(NotificationReceiver.ACTION_PREV, 1))
            .addAction(
                if (isPlaying) R.drawable.baseline_pause_24
                else R.drawable.baseline_play_arrow_24,
                if (isPlaying) "Pausar" else "Reproducir",
                broadcast(NotificationReceiver.ACTION_PLAY_PAUSE, 2)
            )
            .addAction(R.drawable.outline_skip_next_24, "Siguiente",
                broadcast(NotificationReceiver.ACTION_NEXT, 3))
            .addAction(R.drawable.outline_close_24, "Cerrar",
                broadcast(NotificationReceiver.ACTION_CLOSE, 5))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSessionCompat?.sessionToken)
                    .setShowActionsInCompactView(1, 2, 3)
            )
            .build()
    }

    private fun buildBasicNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_music_note_24)
            .setContentTitle("ZyncWave")
            .setContentText("Listo para reproducir")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

    //Helpers

    private fun broadcast(action: String, reqCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            this, reqCode,
            Intent(action).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun updatePlaybackState(player: ExoPlayer) {
        val state = if (player.isPlaying)
            android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING
        else
            android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED

        mediaSessionCompat?.setPlaybackState(
            android.support.v4.media.session.PlaybackStateCompat.Builder()
                .setState(state, player.currentPosition, player.playbackParameters.speed)
                .setActions(
                    android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY or
                            android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE or
                            android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            android.support.v4.media.session.PlaybackStateCompat.ACTION_SEEK_TO
                )
                .build()
        )
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
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}