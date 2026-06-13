package com.example.zyncwave2.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.zyncwave2.R
import com.example.zyncwave2.data.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun SplashScreen() {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            listOf(
                "/storage/emulated/0/Download/ZyncWave/Audio",
                "/storage/emulated/0/Download/ZyncWave/Video"
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
        } else {
            // Primera instalación — agregar carpeta por defecto
            val defaultFolder = "/storage/emulated/0/Download/ZyncWave/Audio"
            val defaultFolders = setOf(defaultFolder)
            PlayerState.selectedFolders.value = defaultFolders
            saveFolders(context, defaultFolders)
            saveFirstLaunchDone(context)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xff191c1f)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.logo),
            contentDescription = "ZyncWave Logo",
            modifier = Modifier.fillMaxWidth(0.5f),
            contentScale = ContentScale.Fit
        )
    }
}