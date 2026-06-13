package com.example.zyncwave2.data

import android.content.Context
import android.os.FileObserver
import androidx.compose.runtime.mutableStateOf
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Estado mínimo compartido entre MusicService y PlayerViewModel.
 * Solo es un puente de datos — toda la lógica vive en PlayerViewModel.
 */
object PlayerState {

    // ── ExoPlayer ─────────────────────────────────────────────────────────────
    var exoPlayer: ExoPlayer? = null

    // ── Carpetas seleccionadas ────────────────────────────────────────────────
    val selectedFolders = mutableStateOf<Set<String>>(emptySet())

    // ── Estado mínimo para MusicService ──────────────────────────────────────
    val currentSong  = MutableStateFlow<Songs?>(null)
    val currentIndex = MutableStateFlow(0)
    val songsList    = MutableStateFlow<List<Songs>>(emptyList())
    val isPlaying    = MutableStateFlow(false)

    //Flags globales del backhandler
    val selectedArtist     = MutableStateFlow<String?>(null)
    val selectedAlbum      = MutableStateFlow<String?>(null)
    val selectedSection    = MutableStateFlow<String?>(null)
    val selectedPlaylistId = MutableStateFlow<Long?>(null)

    //Flag para la cola
    val showQueue = MutableStateFlow(false)

    var isQueueExpanded: Boolean = false


    //Al seleccionar una canción se dirige al playerscreen
    val navigateToPlayer = MutableStateFlow(false)


    // ── Posición restaurada al reabrir la app ─────────────────────────────────
    var lastRestoredPosition: Long = 0L

    // ── Contexto de cola ──────────────────────────────────────────────────────

    /**
     * De dónde viene la cola activa.
     * Se guarda en SharedPreferences para restaurarla correctamente al reabrir.
     *
     * - ALL_SONGS  → lista completa de canciones
     * - FAVORITES  → canciones marcadas como favoritas
     * - RECENT     → canciones ordenadas por fecha de agregado (id desc)
     * - PLAYLIST   → lista creada por el usuario (queueSourceId = playlist id)
     * - ALBUM      → canciones de un álbum (queueSourceId = albumName)
     * - ARTIST     → canciones de un artista (queueSourceId = artistName)
     * - FOLDER     → canciones de una carpeta (queueSourceId = folder path)
     */
    enum class QueueSource {
        ALL_SONGS, FAVORITES, RECENT, PLAYLIST, ALBUM, ARTIST, FOLDER
    }

    // ── FileObserver para detectar archivos nuevos en carpetas ────────────────

    private val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fileObservers: List<FileObserver> = emptyList()

    private val audioExtensions = setOf("mp3", "m4a", "opus", "flac", "ogg", "wav", "aac")

    fun startWatchingFolders(context: Context, onNewFile: suspend () -> Unit) {
        stopWatchingFolders()
        fileObservers = selectedFolders.value.map { folderPath ->
            @Suppress("DEPRECATION")
            object : FileObserver(folderPath, CREATE or MOVED_TO) {
                override fun onEvent(event: Int, path: String?) {
                    if (path == null) return
                    val ext = path.substringAfterLast('.', "").lowercase()
                    if (ext !in audioExtensions) return
                    android.util.Log.d("FileObserver", "Nuevo archivo detectado: $path en $folderPath")
                    observerScope.launch { onNewFile() }
                }
            }.also { it.startWatching() }
        }
        android.util.Log.d("FileObserver", "Vigilando ${fileObservers.size} carpetas")
    }


    fun stopWatchingFolders() {
        fileObservers.forEach { it.stopWatching() }
        fileObservers = emptyList()
    }

    // ── Persistencia de sesión ────────────────────────────────────────────────

    /**
     * Guarda el estado completo de la sesión:
     * posición, índice, canción actual y contexto de cola.
     */
    fun saveLastSession(
        context: Context,
        positionMs: Long,
        queueSource: QueueSource = QueueSource.ALL_SONGS,
        queueSourceId: String = ""
    ) {
        val song = currentSong.value ?: return
        context.getSharedPreferences("player_session", Context.MODE_PRIVATE)
            .edit()
            .putLong("song_id",        song.id)
            .putLong("position_ms",    positionMs)
            .putInt("song_index",      currentIndex.value)
            .putString("queue_source",    queueSource.name)
            .putString("queue_source_id", queueSourceId)
            .apply()
    }

    /**
     * Carga la sesión guardada.
     * Retorna Triple(songId, positionMs, Pair(queueSource, queueSourceId))
     * o null si no hay sesión guardada.
     */
    fun loadLastSession(
        context: Context
    ): Triple<Long, Long, Pair<QueueSource, String>>? {
        val prefs = context.getSharedPreferences("player_session", Context.MODE_PRIVATE)
        val songId   = prefs.getLong("song_id", -1L)
        if (songId == -1L) return null

        val position = prefs.getLong("position_ms", 0L)
        val index    = prefs.getInt("song_index", 0)
        val source   = try {
            QueueSource.valueOf(
                prefs.getString("queue_source", "ALL_SONGS") ?: "ALL_SONGS"
            )
        } catch (e: IllegalArgumentException) {
            QueueSource.ALL_SONGS
        }
        val sourceId = prefs.getString("queue_source_id", "") ?: ""

        currentIndex.value = index
        return Triple(songId, position, Pair(source, sourceId))
    }

    /**
     * Borra la sesión guardada.
     */
    fun clearSession(context: Context) {
        context.getSharedPreferences("player_session", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }


}