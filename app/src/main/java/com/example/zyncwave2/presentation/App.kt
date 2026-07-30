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
        android.util.Log.d("PERF", "App.onCreate START: ${System.currentTimeMillis()}")

        // Inicializar yt-dlp y ffmpeg en hilo IO para no bloquear el main thread
        // y para que la actualización no interfiera con las primeras llamadas
        CoroutineScope(Dispatchers.IO).launch {
            try {
                YoutubeDL.getInstance().init(this@App)
                FFmpeg.getInstance().init(this@App)
                android.util.Log.d("YTDLP", "✓ Init exitoso")

                // Actualizar antes de marcar como listo para evitar conflictos
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(
                        this@App,
                        YoutubeDL.UpdateChannel.STABLE
                    )
                    android.util.Log.d("YTDLP", "yt-dlp actualizado")
                } catch (e: Exception) {
                    // Si falla la actualización no importa, igual puede funcionar
                    android.util.Log.w("YTDLP", "Update falló (no crítico): ${e.message}")
                }

                // Marcar como listo DESPUÉS de init + update
                isYtDlpReady = true
                android.util.Log.d("YTDLP", "✓ yt-dlp listo para usar")

            } catch (e: Exception) {
                android.util.Log.e("YTDLP", "✗ Init falló: ${e.message}")
                isYtDlpReady = false
            }

            val paths = listOf(
                File(filesDir, "youtubedl-android/yt-dlp"),
                File(filesDir, "youtubedl-android/yt-dlp.zip"),
                File(filesDir, "youtubedl-android"),
            )
            paths.forEach {
                android.util.Log.d("YTDLP_PATH", "${it.absolutePath} existe: ${it.exists()}")
            }
// También lista el directorio
            File(filesDir, "youtubedl-android").listFiles()?.forEach {
                android.util.Log.d("YTDLP_PATH", "archivo: ${it.name}")
            }
        }

        startService(android.content.Intent(this, MusicService::class.java))
        android.util.Log.d("PERF", "App.onCreate startService: ${System.currentTimeMillis()}")

        FavoritesManager.init(this)
        PlaylistManager.init(this)

        createAppFolders()
        android.util.Log.d("PERF", "App.onCreate END: ${System.currentTimeMillis()}")
    }


    private fun createAppFolders() {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val audioDir = File(downloadsDir, "ZyncWave/Audio")
            val videoDir = File(downloadsDir, "ZyncWave/Video")
            if (!audioDir.exists()) audioDir.mkdirs()
            if (!videoDir.exists()) videoDir.mkdirs()
            android.util.Log.d("APP", "Carpetas ZyncWave creadas en Downloads")
        } catch (e: Exception) {
            android.util.Log.e("APP", "Error creando carpetas: ${e.message}")
        }
    }
}