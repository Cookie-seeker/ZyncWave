package com.example.zyncwave2.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.zyncwave2.data.PlayerState
import com.example.zyncwave2.ui.theme.loadSavedFolders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        startService(Intent(this, MusicService::class.java))

        var isLoading = true

        // Mantener splash visible mientras carga
        splashScreen.setKeepOnScreenCondition { isLoading }

        // Cargar sesión anterior

        lifecycleScope.launch(Dispatchers.IO) {
            val savedFolders = loadSavedFolders(this@SplashActivity)
            if (savedFolders.isNotEmpty()) {
                PlayerState.selectedFolders.value = savedFolders
            }

            val session = PlayerState.loadLastSession(this@SplashActivity)
            if (session != null) {
                val (songId, positionMs) = session
                val songs = com.example.zyncwave2.data.getSongs(
                    this@SplashActivity,
                    PlayerState.selectedFolders.value
                )
                PlayerState.songsList.value = songs
                val index = songs.indexOfFirst { it.id == songId }
                if (index >= 0) {
                    PlayerState.currentIndex.value = index
                    PlayerState.currentSong.value = songs[index]
                    PlayerState.lastRestoredPosition = positionMs
                }
            }

            withContext(Dispatchers.Main) {
                isLoading = false  // ← oculta el splash
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                finish()
            }
        }
    }
}