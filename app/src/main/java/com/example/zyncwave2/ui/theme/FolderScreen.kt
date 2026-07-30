package com.example.zyncwave2.ui.theme

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.zyncwave2.presentation.PlayerViewModel
import java.io.File

@Composable
fun FolderScreen(
    songs: List<Songs>,
    onSongClick: (songs: List<Songs>, position: Int) -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    val context = LocalContext.current
    val selectedFolders = PlayerState.selectedFolders
    var expandedFolder by remember { mutableStateOf<String?>(null) }

    val songsByFolder = remember(songs, selectedFolders.value) {
        selectedFolders.value.associateWith { folderPath ->
            songs.filter { File(it.data).parent == folderPath }
        }
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
            .statusBarsPadding()
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
            // Estructura aplanada: carpetas y sus canciones son items al mismo nivel
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                val folderList = selectedFolders.value.toList()

                folderList.forEach { folderPath ->
                    val folderName = File(folderPath).name
                    val folderSongs = songsByFolder[folderPath] ?: emptyList()
                    val isExpanded = expandedFolder == folderPath

                    // Item: header de la carpeta
                    item(key = "folder_$folderPath") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(Color(0x15ffffff), RoundedCornerShape(12.dp))
                        ) {
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
                        }
                    }

                    // Items: canciones de la carpeta (solo si está expandida)
                    if (isExpanded) {
                        if (folderSongs.isEmpty()) {
                            item(key = "${folderPath}_empty") {
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
                            }
                        } else {
                            // Ahora sí en el LazyListScope correcto — sin anidar
                            itemsIndexed(
                                items = folderSongs,
                                key = { index, song -> "${folderPath}_${song.id}_$index" }
                            ) { index, song ->
                                SongsListItem(
                                    song = song,
                                    onClick = {
                                        playerViewModel?.setQueueSource(
                                            PlayerState.QueueSource.FOLDER, folderPath
                                        )
                                        onSongClick(folderSongs, index)
                                    }
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}