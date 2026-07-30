package com.example.zyncwave2.presentation

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.example.zyncwave2.data.EqualizerManager
import com.example.zyncwave2.data.FavoritesManager
import com.example.zyncwave2.data.LyricsManager
import com.example.zyncwave2.data.MetadataRepository
import com.example.zyncwave2.data.PlayerState
import com.example.zyncwave2.data.PlayerUiState
import com.example.zyncwave2.data.RepeatMode
import com.example.zyncwave2.data.SongRepository
import com.example.zyncwave2.data.Songs
import com.example.zyncwave2.data.WriteResult
import com.example.zyncwave2.data.db.AppDatabase
import com.example.zyncwave2.ui.theme.artCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context get() = getApplication<Application>().applicationContext

    private val _state = MutableStateFlow(PlayerUiState())
    val state = _state.asStateFlow()

    // ── ExoPlayer listener ────────────────────────────────────────────────────

    private val playerListener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            PlayerState.isPlaying.value = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = PlayerState.exoPlayer ?: return
            if (playbackState == Player.STATE_READY) {
                _state.update { it.copy(duration = player.duration.coerceAtLeast(0L)) }
            }
            if (playbackState == Player.STATE_ENDED) {
                handleSongEnded()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val player = PlayerState.exoPlayer ?: return
            val realIndex = player.currentMediaItemIndex
            val song = _state.value.songsList.getOrNull(realIndex) ?: return
            val activeIndex = _state.value.activeList.indexOfFirst { it.data == song.data }
                .takeIf { it >= 0 } ?: realIndex
            if (activeIndex != _state.value.currentIndex) {
                onIndexChanged(activeIndex)
            }
        }
    }

    init {
        PlayerState.exoPlayer?.addListener(playerListener)
        startPositionTicker()

        viewModelScope.launch {
            snapshotFlow { FavoritesManager.favoriteIds.toList() }
                .collect {
                    val song = _state.value.currentSong ?: return@collect
                    _state.update { state ->
                        state.copy(isFavorite = FavoritesManager.isFavorite(song.id))
                    }
                }
        }

        // Command bus — NotificationReceiver → ViewModel
        viewModelScope.launch {
            PlayerState.pendingCommand.collect { command ->
                command ?: return@collect
                when (command) {
                    is PlayerState.PlayerCommand.PlayPause      -> togglePlaying()
                    is PlayerState.PlayerCommand.Next           -> next()
                    is PlayerState.PlayerCommand.Prev           -> prev()
                    is PlayerState.PlayerCommand.ToggleFavorite -> toggleFavorite()
                }
                PlayerState.pendingCommand.value = null
            }
        }
    }

    // ── Ticker de posición ────────────────────────────────────────────────────

    private fun startPositionTicker() {
        viewModelScope.launch {
            var ticksSinceLastSave = 0
            while (true) {
                delay(200)
                val player = PlayerState.exoPlayer ?: continue
                if (player.isPlaying) {
                    val pos = player.currentPosition
                    _state.update { it.copy(elapsed = pos) }
                    ticksSinceLastSave++
                    if (pos > 0 && ticksSinceLastSave >= 25) {
                        PlayerState.saveLastSession(
                            context, pos,
                            _state.value.queueSource,
                            _state.value.queueSourceId
                        )
                        ticksSinceLastSave = 0
                    }
                }
            }
        }
    }

    // ── Queue source ──────────────────────────────────────────────────────────

    fun setQueueSource(source: PlayerState.QueueSource, sourceId: String) {
        _state.update { it.copy(queueSource = source, queueSourceId = sourceId) }
    }

    // ── Preload — muestra UI sin esperar ExoPlayer ────────────────────────────

    /**
     * Setea metadata de la canción en el state SIN tocar ExoPlayer.
     * Llamado desde MainActivity al restaurar sesión para que PlayerScreen
     * aparezca inmediatamente mientras ExoPlayer se inicializa en background.
     *
     * El flujo es:
     *   1. preloadSongMeta() → UI visible al instante
     *   2. initPlaybackRestored() → ExoPlayer listo, audio conectado
     */
    fun preloadSongMeta(songsList: List<Songs>, index: Int) {
        val song = songsList.getOrNull(index) ?: return
        _state.update {
            it.copy(
                songsList    = songsList,
                currentIndex = index,
                currentSong  = song
            )
        }
        PlayerState.currentSong.value  = song
        PlayerState.currentIndex.value = index

        // Letra y favorito en background — no bloquean la UI
        viewModelScope.launch(Dispatchers.IO) {
            // Bitmap PRIMERO — antes de que el tab 0 sea visible
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(song.data)
                val art = retriever.embeddedPicture
                retriever.release()
                if (art != null) {
                    val bitmap = android.graphics.BitmapFactory
                        .decodeByteArray(art, 0, art.size)
                    artCache.put(song.data, bitmap)
                }
            } catch (e: Exception) { }

            // Letra y favorito después
            val lyrics   = LyricsManager.loadLyrics(context, song.id)
            val favorite = FavoritesManager.isFavorite(song.id)
            _state.update {
                it.copy(currentLyrics = lyrics, isFavorite = favorite)
            }
        }
    }

    // ── Inicializar reproducción ──────────────────────────────────────────────

    @OptIn(UnstableApi::class)
    fun initPlayback(songsList: List<Songs>, initialIndex: Int) {
        val player  = PlayerState.exoPlayer ?: return
        val current = _state.value

        if (current.songsList == songsList && player.mediaItemCount == songsList.size) {
            if (current.currentIndex != initialIndex) seekToRealIndex(initialIndex)
            return
        }

        _state.update { it.copy(songsList = songsList, currentIndex = initialIndex) }

        val mediaItems = songsList.map { s ->
            MediaItem.Builder()
                .setUri(s.data)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(s.title)
                        .setArtist(s.artists)
                        .setAlbumTitle(s.albumName)
                        .setArtworkUri(
                            ContentUris.withAppendedId(
                                Uri.parse("content://media/external/audio/albumart"),
                                s.albumId
                            )
                        )
                        .build()
                )
                .build()
        }

        player.setMediaItems(mediaItems, initialIndex, 0L)
        player.prepare()
        player.play()

        updateSongMeta(initialIndex)

        val audioSessionId = player.audioSessionId
        if (audioSessionId != 0) EqualizerManager.init(audioSessionId)

        // Precalentar caché de carátula en background
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(songsList[initialIndex].data)
                val art = retriever.embeddedPicture
                retriever.release()
                if (art != null) {
                    val bitmap = android.graphics.BitmapFactory
                        .decodeByteArray(art, 0, art.size)
                    com.example.zyncwave2.ui.theme.artCache
                        .put(songsList[initialIndex].data, bitmap)
                }
            } catch (e: Exception) { /* no crítico */ }
        }
    }

    @OptIn(UnstableApi::class)
    fun initPlaybackRestored(songsList: List<Songs>, initialIndex: Int, positionMs: Long) {
        val player = PlayerState.exoPlayer ?: return

        _state.update { it.copy(songsList = songsList, currentIndex = initialIndex) }

        val mediaItems = songsList.map { s ->
            MediaItem.Builder()
                .setUri(s.data)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(s.title)
                        .setArtist(s.artists)
                        .setAlbumTitle(s.albumName)
                        .setArtworkUri(
                            ContentUris.withAppendedId(
                                Uri.parse("content://media/external/audio/albumart"),
                                s.albumId
                            )
                        )
                        .build()
                )
                .build()
        }

        player.setMediaItems(mediaItems, initialIndex, positionMs)
        player.prepare()
        // NO player.play() — queda pausado en la posición guardada

        updateSongMeta(initialIndex)

        val audioSessionId = player.audioSessionId
        if (audioSessionId != 0) EqualizerManager.init(audioSessionId)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(songsList[initialIndex].data)
                val art = retriever.embeddedPicture
                retriever.release()
                if (art != null) {
                    val bitmap = android.graphics.BitmapFactory
                        .decodeByteArray(art, 0, art.size)
                    artCache.put(songsList[initialIndex].data, bitmap)
                }
            } catch (e: Exception) { }
        }


    }

    // ── Controles de reproducción ─────────────────────────────────────────────

    fun togglePlaying() {
        val player = PlayerState.exoPlayer ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() {
        val list = _state.value.activeList
        if (list.isEmpty()) return
        val newIndex  = (_state.value.currentIndex + 1) % list.size
        val song      = list[newIndex]
        val realIndex = _state.value.songsList.indexOfFirst { it.data == song.data }
            .coerceAtLeast(0)
        _state.update { it.copy(currentIndex = newIndex) }
        seekToRealIndex(realIndex)
    }

    fun prev() {
        val player = PlayerState.exoPlayer ?: return
        if (player.currentPosition > 3000L) {
            player.seekTo(0L)
            return
        }
        val list = _state.value.activeList
        if (list.isEmpty()) return
        val newIndex = if (_state.value.currentIndex - 1 < 0) list.size - 1
        else _state.value.currentIndex - 1
        val song      = list[newIndex]
        val realIndex = _state.value.songsList.indexOfFirst { it.data == song.data }
            .coerceAtLeast(0)
        _state.update { it.copy(currentIndex = newIndex) }
        seekToRealIndex(realIndex)
    }

    fun seekTo(positionMs: Long) {
        PlayerState.exoPlayer?.seekTo(positionMs)
        _state.update { it.copy(elapsed = positionMs) }
    }

    fun toggleRepeatMode() {
        val next = when (_state.value.repeatMode) {
            RepeatMode.NONE -> RepeatMode.ONE
            RepeatMode.ONE  -> RepeatMode.ALL
            RepeatMode.ALL  -> RepeatMode.NONE
        }
        _state.update { it.copy(repeatMode = next) }
        PlayerState.exoPlayer?.repeatMode = when (next) {
            RepeatMode.NONE -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE  -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL  -> Player.REPEAT_MODE_ALL
        }
    }

    fun toggleShuffle() {
        val current   = _state.value
        val newShuffle = !current.isShuffle
        if (newShuffle) {
            val currentSong = current.currentSong
            val others  = current.songsList.filter { it.data != currentSong?.data }.shuffled()
            val shuffled = if (currentSong != null) listOf(currentSong) + others else others
            _state.update { it.copy(isShuffle = true, shuffledList = shuffled, currentIndex = 0) }
        } else {
            val currentSongData = current.currentSong?.data
            val originalIndex   = current.songsList.indexOfFirst { it.data == currentSongData }
                .coerceAtLeast(0)
            _state.update { it.copy(isShuffle = false, shuffledList = emptyList(), currentIndex = originalIndex) }
        }
    }

    fun toggleFavorite() {
        val song = _state.value.currentSong ?: return
        FavoritesManager.toggleFavorite(context, song.id)
        _state.update { it.copy(isFavorite = FavoritesManager.isFavorite(song.id)) }
    }

    // ── Dialogs / UI flags ────────────────────────────────────────────────────

    fun setShowMenu(show: Boolean)          = _state.update { it.copy(showMenu = show) }
    fun setShowLyrics(show: Boolean)        = _state.update { it.copy(showLyrics = show) }
    fun setShowLyricsEditor(show: Boolean)  = _state.update { it.copy(showLyricsEditor = show) }
    fun setShowAddToPlaylist(show: Boolean) = _state.update { it.copy(showAddToPlaylist = show) }
    fun setShowTagEditor(show: Boolean)     = _state.update { it.copy(showTagEditor = show) }
    fun setShowEqualizer(show: Boolean)     = _state.update { it.copy(showEqualizer = show) }
    fun setShowQueue(show: Boolean) {
        _state.update { it.copy(showQueue = show) }
        PlayerState.showQueue.value = show
    }

    // ── Letras ────────────────────────────────────────────────────────────────

    fun saveLyrics(lyrics: String) {
        val song = _state.value.currentSong ?: return
        viewModelScope.launch(Dispatchers.IO) {
            LyricsManager.saveLyrics(context, song.id, lyrics)
        }
        _state.update { it.copy(currentLyrics = lyrics, showLyricsEditor = false) }
    }

    // ── Tag editor ────────────────────────────────────────────────────────────

    fun onTagsSaved(
        newTitle: String, newArtist: String, newAlbum: String,
        newGenre: String, newTrackNumber: Int, newDiscNumber: Int
    ) {
        val current = _state.value
        val song    = current.currentSong ?: return

        val updatedSong = song.copy(
            title       = newTitle,
            artists     = newArtist,
            albumName   = newAlbum,
            genre       = newGenre,
            trackNumber = newTrackNumber,
            discNumber  = newDiscNumber
        )

        val updatedSongsList = current.songsList.map {
            if (it.data == song.data) updatedSong else it
        }
        val updatedShuffled = current.shuffledList.map {
            if (it.data == song.data) updatedSong else it
        }

        _state.update {
            it.copy(
                currentSong  = updatedSong,
                songsList    = updatedSongsList,
                shuffledList = updatedShuffled,
                showTagEditor = false,
                imageVersion  = it.imageVersion + 1
            )
        }

        PlayerState.currentSong.value = updatedSong
        PlayerState.songsList.value   = updatedSongsList
    }

    fun saveTagsWithPause(
        songId: Long,
        filePath: String,
        title: String,
        artist: String,
        album: String,
        genre: String,
        trackNumber: Int,
        discNumber: Int,
        artworkUri: android.net.Uri?,
        onResult: (WriteResult) -> Unit
    ) {
        val player     = PlayerState.exoPlayer
        val wasPlaying = player?.isPlaying == true
        val position   = player?.currentPosition ?: 0L
        val currentIdx = player?.currentMediaItemIndex ?: 0

        player?.pause()

        viewModelScope.launch {
            val metaRepo = MetadataRepository(context)
            val result   = metaRepo.saveTags(
                songId      = songId,
                filePath    = filePath,
                title       = title,
                artist      = artist,
                album       = album,
                genre       = genre,
                trackNumber = trackNumber,
                discNumber  = discNumber,
                artworkUri  = artworkUri
            )

            if (result is WriteResult.Success) {
                player?.let {
                    it.stop()
                    delay(300)
                    it.prepare()
                    it.seekTo(currentIdx, position)
                    if (wasPlaying) it.play()
                }
            } else {
                if (wasPlaying) {
                    player?.seekTo(position)
                    player?.play()
                }
            }

            onResult(result)
        }
    }

    // ── Biblioteca ────────────────────────────────────────────────────────────

    fun rescanLibrary() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                SongRepository(context).fullScan(PlayerState.selectedFolders.value)
            }
            val updatedSongs = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).songDao()
                    .getAllFlow()
                    .first()
                    .filter { entity ->
                        PlayerState.selectedFolders.value.isEmpty() ||
                                PlayerState.selectedFolders.value.any { entity.data.startsWith(it) }
                    }
                    .map { it.toSongs() }
            }
            _state.update { it.copy(songsList = updatedSongs) }
            PlayerState.songsList.value = updatedSongs
        }
    }

    private var lastSyncedFoldersHash = Int.MIN_VALUE

    fun syncLibrary() {
        val hash = PlayerState.selectedFolders.value.hashCode()
        if (hash == lastSyncedFoldersHash) return
        lastSyncedFoldersHash = hash
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                SongRepository(context).syncWithDisk(PlayerState.selectedFolders.value)
            }
        }
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private fun seekToRealIndex(realIndex: Int) {
        val player = PlayerState.exoPlayer ?: return
        player.seekTo(realIndex, 0L)
        player.play()
        updateSongMeta(_state.value.currentIndex)
        PlayerState.currentIndex.value = _state.value.currentIndex
    }

    private fun onIndexChanged(index: Int) {
        _state.update { it.copy(currentIndex = index, elapsed = 0L) }
        updateSongMeta(index)
        PlayerState.currentIndex.value = index
    }

    private fun updateSongMeta(index: Int) {
        val list = _state.value.activeList
        val song = list.getOrNull(index) ?: return

        viewModelScope.launch {
            val lyrics   = withContext(Dispatchers.IO) {
                LyricsManager.loadLyrics(context, song.id)
            }
            val favorite = FavoritesManager.isFavorite(song.id)
            _state.update {
                it.copy(
                    currentSong   = song,
                    currentLyrics = lyrics,
                    isFavorite    = favorite,
                    showLyrics    = false
                )
            }
            PlayerState.currentSong.value  = song
            PlayerState.currentIndex.value = index
        }
    }

    private fun handleSongEnded() {
        val list = _state.value.activeList
        when (_state.value.repeatMode) {
            RepeatMode.ONE  -> { /* ExoPlayer maneja REPEAT_MODE_ONE */ }
            RepeatMode.ALL  -> next()
            RepeatMode.NONE -> {
                val isLast = _state.value.currentIndex >= list.size - 1
                if (isLast) {
                    val player = PlayerState.exoPlayer ?: return
                    _state.update { it.copy(currentIndex = 0, elapsed = 0L) }
                    player.seekTo(0, 0L)
                    player.pause()
                    updateSongMeta(0)
                    _state.update { it.copy(queueEndedEvent = true) }
                } else {
                    next()
                }
            }
        }
    }

    // ── Back handler ──────────────────────────────────────────────────────────

    fun handleBack(): Boolean {
        return when {
            _state.value.showQueue         -> { setShowQueue(false);         true }
            _state.value.showMenu          -> { setShowMenu(false);          true }
            _state.value.showEqualizer     -> { setShowEqualizer(false);     true }
            _state.value.showTagEditor     -> { setShowTagEditor(false);     true }
            _state.value.showAddToPlaylist -> { setShowAddToPlaylist(false); true }
            _state.value.showLyricsEditor  -> { setShowLyricsEditor(false);  true }
            _state.value.showLyrics        -> { setShowLyrics(false);        true }
            else                           -> false
        }
    }

    fun playFromQueue(index: Int) {
        val list      = _state.value.activeList
        val song      = list.getOrNull(index) ?: return
        val realIndex = _state.value.songsList.indexOfFirst { it.data == song.data }
            .coerceAtLeast(0)
        _state.update { it.copy(currentIndex = index) }
        seekToRealIndex(realIndex)
        setShowQueue(false)
    }

    fun consumeQueueEndedEvent() {
        _state.update { it.copy(queueEndedEvent = false) }
    }

    override fun onCleared() {
        PlayerState.exoPlayer?.removeListener(playerListener)
        super.onCleared()
    }
}