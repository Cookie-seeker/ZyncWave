package com.example.zyncwave2.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

object FavoritesManager {

    private const val PREFS_NAME = "favorites_prefs"
    private const val KEY_FAVORITES = "favorite_ids"

    // Lista observable en memoria
    val favoriteIds: SnapshotStateList<Long> = mutableStateListOf()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        favoriteIds.clear()
        favoriteIds.addAll(saved.map { it.toLong() })
    }

    fun toggleFavorite(context: Context, songId: Long) {
        if (favoriteIds.contains(songId)) {
            favoriteIds.remove(songId)
        } else {
            favoriteIds.add(songId)
        }
        save(context)
    }

    fun isFavorite(songId: Long): Boolean {
        return favoriteIds.contains(songId)
    }

    private fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet(KEY_FAVORITES, favoriteIds.map { it.toString() }.toSet())
            .apply()
    }
}