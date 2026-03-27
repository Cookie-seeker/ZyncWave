package com.example.zyncwave2.data

import android.content.Context
import java.io.File

object LyricsManager {

    private fun getLyricsFile(context: Context, songId: Long): File {
        val dir = File(context.filesDir, "lyrics")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$songId.txt")
    }

    fun saveLyrics(context: Context, songId: Long, lyrics: String) {
        try {
            getLyricsFile(context, songId).writeText(lyrics)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadLyrics(context: Context, songId: Long): String {
        return try {
            val file = getLyricsFile(context, songId)
            if (file.exists()) file.readText() else ""
        } catch (e: Exception) {
            ""
        }
    }

    fun hasLyrics(context: Context, songId: Long): Boolean {
        return getLyricsFile(context, songId).exists()
    }

    fun deleteLyrics(context: Context, songId: Long) {
        try {
            getLyricsFile(context, songId).delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}