package com.example.zyncwave2.ui.theme

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zyncwave2.R
import com.example.zyncwave2.data.PlayerState
import java.io.File

@Composable

fun FolderPickerScreen(
    onFinish: () -> Unit

) {
    val context = LocalContext.current


    val defaultFolder = "/storage/emulated/0/Music/ZyncWave/Audio"

    val selectedFolders = remember {
        mutableStateListOf(defaultFolder)
    }



    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val path = getFolderPath(context, it)
            if (path != null && !selectedFolders.contains(path)) {
                selectedFolders.add(path)
            }
        }
    }



    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xff191c1f))
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Carpetas a escanear",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = BebasNeue
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Seleccione las carpetas donde guarda sus archivos de audio.",
                color = Color(0xffbbbbbb),
                fontSize = 14.sp,
                fontFamily = Nunito
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Botón agregar carpeta
            OutlinedButton(
                onClick = { folderPicker.launch(null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2C2E))
            ) {
                Icon(
                    painterResource(R.drawable.outline_add_24),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "AGREGAR CARPETA",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lista de carpetas seleccionadas
            if (selectedFolders.isNotEmpty()) {
                Text(
                    "Carpetas seleccionadas:",
                    color = Color(0xffbbbbbb),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(selectedFolders) { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .background(Color(0x20ffffff), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painterResource(R.drawable.outline_folder_24),
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = folder,
                                color = Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { selectedFolders.remove(folder) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    painterResource(R.drawable.outline_delete_24),
                                    contentDescription = null,
                                    tint = Color(0xFFe91e63),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No hay carpetas seleccionadas",
                        color = Color(0x60ffffff),
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Botón continuar
        Button(
            onClick = {
                if (selectedFolders.isNotEmpty()) {
                    PlayerState.selectedFolders.value = selectedFolders.toSet()
                    saveFirstLaunchDone(context)
                    saveFolders(context, selectedFolders.toSet())
                }
                onFinish()
            },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            enabled = selectedFolders.isNotEmpty(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2C2C2E),
                disabledContainerColor = Color(0xFF2C2C2E)
            )
        ) {
            Text(
                "Continuar",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

fun getFolderPath(context: Context, uri: Uri): String? {
    val docId = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
        ?.uri?.lastPathSegment ?: return null
    return if (docId.contains(":")) {
        val parts = docId.split(":")
        if (parts[0] == "primary") "/storage/emulated/0/${parts[1]}"
        else "/storage/${parts[0]}/${parts[1]}"
    } else docId
}

fun saveFirstLaunchDone(context: Context) {
    context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        .edit().putBoolean("first_launch_done", true).apply()
}

fun saveFolders(context: Context, folders: Set<String>) {
    android.util.Log.d("SPLASH", "Guardando carpetas: $folders")
    context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        .edit().putStringSet("selected_folders", folders).apply()
}

fun isFirstLaunch(context: Context): Boolean {
    return !context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        .getBoolean("first_launch_done", false)
}

fun loadSavedFolders(context: Context): Set<String> {
    return context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        .getStringSet("selected_folders", emptySet()) ?: emptySet()
}

