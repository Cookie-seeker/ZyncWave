package com.example.zyncwave2.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.media3.exoplayer.ExoPlayer

object PlayerState {
    val currentSong = mutableStateOf<Songs?>(null)
    val isPlaying = mutableStateOf(false)
    val currentIndex = mutableStateOf(0)
    val songsList = mutableStateOf<List<Songs>>(emptyList())
    var exoPlayer: ExoPlayer? = null
    // Carpetas seleccionadas por el usuario
    val selectedFolders = mutableStateOf<Set<String>>(emptySet())
    var lastRestoredPosition: Long = 0L

    fun saveLastSession(context: Context, positionMs: Long) {
        val song = currentSong.value ?: return
        context.getSharedPreferences("player_session", Context.MODE_PRIVATE)
            .edit()
            .putLong("song_id", song.id)
            .putLong("position_ms", positionMs)
            .putInt("song_index", currentIndex.value)
            .apply()
        android.util.Log.d("PlayerState", "Sesión guardada: ${song.title} @ ${positionMs}ms")
    }

    fun loadLastSession(context: Context): Pair<Long, Long>? {
        val prefs = context.getSharedPreferences("player_session", Context.MODE_PRIVATE)
        val songId = prefs.getLong("song_id", -1L)
        val position = prefs.getLong("position_ms", 0L)
        val index = prefs.getInt("song_index", 0)
        if (songId == -1L) return null
        currentIndex.value = index
        return Pair(songId, position)
    }

    fun clearSession(context: Context) {
        context.getSharedPreferences("player_session", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}