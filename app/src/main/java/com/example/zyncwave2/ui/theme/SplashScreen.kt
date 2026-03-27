package com.example.zyncwave2.ui.theme

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.zyncwave2.R
import com.example.zyncwave2.data.PlayerState
import com.example.zyncwave2.presentation.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun SplashScreen() {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            listOf(
                "/storage/emulated/0/Music/ZyncWave/Audio",
                "/storage/emulated/0/Music/ZyncWave/Video"
            ).forEach { path ->
                val folder = java.io.File(path)
                if (!folder.exists()) folder.mkdirs()
            }
        }

        delay(2000)
        val savedFolders = loadSavedFolders(context)
        android.util.Log.d("SPLASH", "Carpetas cargadas: $savedFolders")
        if (savedFolders.isNotEmpty()) {
            PlayerState.selectedFolders.value = savedFolders
        }
        context.startActivity(Intent(context, MainActivity::class.java))
        (context as? android.app.Activity)?.finish()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.intro_pic),
            contentDescription = null,
            modifier = Modifier.align(Alignment.TopCenter),
            contentScale = ContentScale.Fit,
        )
    }
}