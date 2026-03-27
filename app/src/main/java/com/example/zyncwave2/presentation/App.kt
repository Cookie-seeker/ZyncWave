package com.example.zyncwave2.presentation

import android.app.Application
import android.os.Environment
import com.example.zyncwave2.data.FavoritesManager
import com.example.zyncwave2.data.PlaylistManager
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class App : Application() {
    companion object {
        var isYtDlpReady = false
    }

    override fun onCreate() {
        super.onCreate()

        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            isYtDlpReady = true
            android.util.Log.d("YTDLP", "✓ Init exitoso")
        } catch (e: Exception) {
            android.util.Log.e("YTDLP", "✗ Init falló: ${e.message}")
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                YoutubeDL.getInstance().updateYoutubeDL(
                    this@App,
                    YoutubeDL.UpdateChannel.STABLE
                )
                android.util.Log.d("YTDLP", "yt-dlp actualizado")
            } catch (e: Exception) {
                android.util.Log.e("YTDLP", "Update falló: ${e.message}")
            }
        }

        startService(android.content.Intent(this, MusicService::class.java))

        FavoritesManager.init(this)
        PlaylistManager.init(this)

        createAppFolders()
    }

    private fun createAppFolders() {
        try {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val audioDir = File(musicDir, "ZyncWave/Audio")
            val videoDir = File(musicDir, "ZyncWave/Video")
            if (!audioDir.exists()) audioDir.mkdirs()
            if (!videoDir.exists()) videoDir.mkdirs()
            android.util.Log.d("APP", "Carpetas ZyncWave creadas")
        } catch (e: Exception) {
            android.util.Log.e("APP", "Error creando carpetas: ${e.message}")
        }
    }
}