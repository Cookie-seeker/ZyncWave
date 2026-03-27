package com.example.zyncwave2.ui.theme

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zyncwave2.R
import com.example.zyncwave2.data.PlayerState
import com.example.zyncwave2.data.Songs
import java.io.File

@Composable
fun FolderScreen(
    songs: List<Songs>,
    onSongClick: (songs: List<Songs>, position: Int) -> Unit
) {
    val context = LocalContext.current
    val selectedFolders = PlayerState.selectedFolders
    var expandedFolder by remember { mutableStateOf<String?>(null) }

    // Precalcular canciones por carpeta una sola vez → sin delay al expandir
    val songsByFolder = remember(songs, selectedFolders.value) {
        selectedFolders.value.associateWith { folderPath ->
            songs.filter { File(it.data).parent == folderPath }
        }
    }

    // Picker nativo — mismo sistema que FolderPickerScreen
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val path = getFolderPath(context, it)
            if (path != null && !selectedFolders.value.contains(path)) {
                val newFolders = selectedFolders.value + path
                selectedFolders.value = newFolders
                saveFolders(context, newFolders)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Carpetas",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            IconButton(onClick = { folderPicker.launch(null) }) {
                Icon(
                    painterResource(R.drawable.outline_add_24),
                    contentDescription = "Agregar carpeta",
                    tint = Color.White
                )
            }
        }

        // Estado vacío
        if (selectedFolders.value.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painterResource(R.drawable.outline_folder_24),
                        contentDescription = null,
                        tint = Color(0x40ffffff),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No hay carpetas seleccionadas",
                        color = Color(0x80ffffff),
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Toca + para agregar una carpeta",
                        color = Color(0x50ffffff),
                        fontSize = 13.sp
                    )
                }
            }
        } else {

            // Lista de carpetas seleccionadas
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(selectedFolders.value.toList()) { folderPath ->
                    val folderName = File(folderPath).name
                    val folderSongs = songsByFolder[folderPath] ?: emptyList()
                    val isExpanded = expandedFolder == folderPath

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(Color(0x15ffffff), RoundedCornerShape(12.dp))
                    ) {
                        // Fila de la carpeta
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedFolder = if (isExpanded) null else folderPath
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painterResource(R.drawable.outline_folder_24),
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = folderName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (folderSongs.isEmpty()) folderPath
                                    else "${folderSongs.size} canción${if (folderSongs.size != 1) "es" else ""}",
                                    color = Color(0xffbbbbbb),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            // Botón eliminar carpeta
                            IconButton(
                                onClick = {
                                    val newFolders = selectedFolders.value - folderPath
                                    selectedFolders.value = newFolders
                                    saveFolders(context, newFolders)
                                    if (expandedFolder == folderPath) expandedFolder = null
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    painterResource(R.drawable.outline_delete_24),
                                    contentDescription = "Eliminar carpeta",
                                    tint = Color(0x80ffffff),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            // Flecha expandir/colapsar
                            Icon(
                                painterResource(
                                    if (isExpanded) R.drawable.outline_skip_previous_24
                                    else R.drawable.outline_skip_next_24
                                ),
                                contentDescription = null,
                                tint = Color(0x60ffffff),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Canciones expandidas
                        if (isExpanded) {
                            if (folderSongs.isEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painterResource(R.drawable.baseline_music_note_24),
                                        contentDescription = null,
                                        tint = Color(0x40ffffff),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Sin canciones escaneadas en esta carpeta",
                                        color = Color(0x60ffffff),
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                folderSongs.forEachIndexed { index, song ->
                                    SongsListItem(
                                        song = song,
                                        onClick = { onSongClick(folderSongs, index) }
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}