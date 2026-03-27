package com.example.zyncwave2.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.json.JSONArray
import org.json.JSONObject

data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long>
)

object PlaylistManager {

    private const val PREFS_NAME = "playlists_prefs"
    private const val KEY_PLAYLISTS = "playlists"

    val playlists: SnapshotStateList<Playlist> = mutableStateListOf()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PLAYLISTS, "[]") ?: "[]"
        val array = JSONArray(json)
        playlists.clear()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val ids = obj.getJSONArray("songIds")
            val songIds = (0 until ids.length()).map { ids.getLong(it) }
            playlists.add(Playlist(obj.getLong("id"), obj.getString("name"), songIds))
        }
    }

    fun createPlaylist(context: Context, name: String, songIds: List<Long> = emptyList()) {
        val id = System.currentTimeMillis()
        playlists.add(Playlist(id, name, songIds))
        save(context)
    }

    fun deletePlaylist(context: Context, playlistId: Long) {
        playlists.removeAll { it.id == playlistId }
        save(context)
    }

    fun addSongToPlaylist(context: Context, playlistId: Long, songId: Long) {
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val pl = playlists[index]
            if (!pl.songIds.contains(songId)) {
                playlists[index] = pl.copy(songIds = pl.songIds + songId)
                save(context)
            }
        }
    }

    fun removeSongFromPlaylist(context: Context, playlistId: Long, songId: Long) {
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val pl = playlists[index]
            playlists[index] = pl.copy(songIds = pl.songIds.filter { it != songId })
            save(context)
        }
    }

    fun getSongsForPlaylist(playlistId: Long, allSongs: List<Songs>): List<Songs> {
        val pl = playlists.find { it.id == playlistId } ?: return emptyList()
        return pl.songIds.mapNotNull { id -> allSongs.find { it.id == id } }
    }

    private fun save(context: Context) {
        val array = JSONArray()
        playlists.forEach { pl ->
            val obj = JSONObject()
            obj.put("id", pl.id)
            obj.put("name", pl.name)
            val ids = JSONArray()
            pl.songIds.forEach { ids.put(it) }
            obj.put("songIds", ids)
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_PLAYLISTS, array.toString()).apply()
    }
}