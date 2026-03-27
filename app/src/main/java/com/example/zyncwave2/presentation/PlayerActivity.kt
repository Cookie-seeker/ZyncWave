package com.example.zyncwave2.presentation

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.zyncwave2.data.Songs
import com.example.zyncwave2.ui.theme.PlayerScreen

class PlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val songsList: List<Songs> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("songsList", Songs::class.java) ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Songs>("songsList") ?: emptyList()
        }

        val initialIndex = intent.getIntExtra("position", 0)

        setContent {
            PlayerScreen(
                songsList = songsList,
                initialIndex = initialIndex,
                onBack = { finish() }
            )
        }
    }
}