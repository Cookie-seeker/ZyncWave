package com.example.zyncwave2.data

data class PlayerUiState(
    // ── Canción actual ────────────────────────────────────────────────────────
    val currentSong: Songs? = null,
    val currentIndex: Int = 0,
    val songsList: List<Songs> = emptyList(),

    val queueSource: PlayerState.QueueSource = PlayerState.QueueSource.ALL_SONGS,
    val queueSourceId: String = "",

    // ── Reproducción ──────────────────────────────────────────────────────────
    val isPlaying: Boolean = false,
    val elapsed: Long = 0L,
    val duration: Long = 0L,

    // ── Modos ─────────────────────────────────────────────────────────────────
    val isShuffle: Boolean = false,
    val shuffledList: List<Songs> = emptyList(),
    val repeatMode: RepeatMode = RepeatMode.NONE,

    // ── UI extra ──────────────────────────────────────────────────────────────
    val isFavorite: Boolean = false,
    val currentLyrics: String = "",
    val showLyrics: Boolean = false,

    // ── Dialogs ───────────────────────────────────────────────────────────────
    val showMenu: Boolean = false,
    val showQueue: Boolean = false,
    val showLyricsEditor: Boolean = false,
    val showAddToPlaylist: Boolean = false,
    val showTagEditor: Boolean = false,
    val showEqualizer: Boolean = false,

    // ── Forzar recomposición de portada ───────────────────────────────────────
    val imageVersion: Int = 0,

    // ── Evento de fin de cola ─────────────────────────────────────────────────
    val queueEndedEvent: Boolean = false
) {
    val activeList: List<Songs>
        get() = if (isShuffle) shuffledList else songsList

    val waveformProgress: Float
        get() = if (duration > 0) elapsed.toFloat() / duration else 0f
}

enum class RepeatMode { NONE, ONE, ALL }