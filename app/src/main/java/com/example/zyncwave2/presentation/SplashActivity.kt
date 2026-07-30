package com.example.zyncwave2.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.zyncwave2.data.PlayerState
import com.example.zyncwave2.data.db.AppDatabase
import com.example.zyncwave2.ui.theme.loadSavedFolders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        android.util.Log.d("PERF", "SplashActivity.onCreate START: ${System.currentTimeMillis()}")
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {}

        startService(Intent(this, MusicService::class.java))

        var isLoading = true

        // Mantener splash visible mientras carga
        splashScreen.setKeepOnScreenCondition { isLoading }

        // Cargar sesión anterior

        lifecycleScope.launch(Dispatchers.IO) {
            android.util.Log.d("PERF", "SplashActivity IO START: ${System.currentTimeMillis()}")
            val savedFolders = loadSavedFolders(this@SplashActivity)
            if (savedFolders.isNotEmpty()) {
                PlayerState.selectedFolders.value = savedFolders
            }

            val session = PlayerState.loadLastSession(this@SplashActivity)
            if (session != null) {
                val (songId, positionMs) = session
                val songs = AppDatabase.getInstance(this@SplashActivity)
                    .songDao()
                    .getAllFlow()
                    .first()
                    .filter { entity ->
                        PlayerState.selectedFolders.value.isEmpty() ||
                                PlayerState.selectedFolders.value.any {
                                    entity.data.startsWith(it)
                                }
                    }
                    .map { it.toSongs() }
                PlayerState.songsList.value = songs
                val index = songs.indexOfFirst { it.id == songId }
                if (index >= 0) {
                    PlayerState.currentIndex.value = index
                    PlayerState.currentSong.value = songs[index]
                    PlayerState.lastRestoredPosition = positionMs
                }
            }

            withContext(Dispatchers.Main) {
                android.util.Log.d("PERF", "SplashActivity NAVIGATE: ${System.currentTimeMillis()}")
                isLoading = false
                val savedFolders = loadSavedFolders(this@SplashActivity)
                if (savedFolders.isEmpty()) {
                    // Primera vez — ir al onboarding
                    startActivity(Intent(this@SplashActivity, OnboardingActivity::class.java))
                } else {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                }
                finish()
            }
        }
    }
}