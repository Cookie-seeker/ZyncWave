package com.example.zyncwave2.ui.theme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zyncwave2.R
import com.example.zyncwave2.data.FavoritesManager
import com.example.zyncwave2.data.PlaylistManager
import com.example.zyncwave2.data.Songs

@Composable
fun ListsScreen(
    songs: List<Songs>,
    onSongClick: (List<Songs>, Int) -> Unit
) {
    val context = LocalContext.current
    var selectedSection by remember { mutableStateOf<String?>(null) }
    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val recentSongs = remember(songs.size) {
        songs.sortedByDescending { it.id }
    }

    val favoriteIds = FavoritesManager.favoriteIds
    val favoriteSongs = remember(songs, favoriteIds.size) {
        songs.filter { FavoritesManager.isFavorite(it.id) }
    }

    BackHandler(enabled = selectedPlaylistId != null || selectedSection != null) {
        when {
            selectedPlaylistId != null -> selectedPlaylistId = null
            selectedSection != null -> selectedSection = null
        }
    }

        val playlists = PlaylistManager.playlists

    // Pantalla de playlist personalizada
    selectedPlaylistId?.let { plId ->
        val plSongs = PlaylistManager.getSongsForPlaylist(plId, songs)
        val plName = playlists.find { it.id == plId }?.name ?: ""
        SectionScreen(
            title = plName,
            songs = plSongs,
            onBack = { selectedPlaylistId = null },
            onSongClick = onSongClick,
            showDelete = true,
            onDelete = {
                PlaylistManager.deletePlaylist(context, plId)
                selectedPlaylistId = null
            }
        )
        return
    }

    when (selectedSection) {
        "recent" -> SectionScreen(
            title = "Agregadas recientemente",
            songs = recentSongs,
            onBack = { selectedSection = null },
            onSongClick = onSongClick
        )
        "favorites" -> SectionScreen(
            title = "Favoritos",
            songs = favoriteSongs,
            onBack = { selectedSection = null },
            onSongClick = onSongClick
        )
        else -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xff191c1f), Color(0xff2c2c38))))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Listas",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    modifier = Modifier.padding(top = 44.dp, bottom = 24.dp)
                )

                ListSectionCard(
                    title = "Agregadas recientemente",
                    subtitle = "${recentSongs.size} canciones",
                    icon = R.drawable.outline_more_time_24,
                    iconTint = Color.Black,
                    onClick = { selectedSection = "recent" }
                )

                Spacer(modifier = Modifier.height(12.dp))

                ListSectionCard(
                    title = "Favoritos",
                    subtitle = "${favoriteSongs.size} canciones",
                    icon = R.drawable.baseline_favorite_24,
                    iconTint = Color(0xFFe91e63),
                    onClick = { selectedSection = "favorites" }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mis listas", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.size(36.dp).background(Color(0xFF2C2C2E), shape = CircleShape)
                    ) {
                        Icon(painterResource(R.drawable.outline_add_24), contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (playlists.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(painterResource(R.drawable.outline_queue_music_24),
                                contentDescription = null, tint = Color(0x60ffffff),
                                modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No tienes listas creadas\nToca + para crear una",
                                color = Color(0x80ffffff), fontSize = 14.sp,
                                textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    LazyColumn {
                        items(playlists) { playlist ->
                            val count = playlist.songIds.size
                            ListSectionCard(
                                title = playlist.name,
                                subtitle = "$count canción${if (count != 1) "es" else ""}",
                                icon = R.drawable.outline_queue_music_24,
                                iconTint = Color.Black,
                                onClick = { selectedPlaylistId = playlist.id }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    // Dialog crear lista
    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                PlaylistManager.createPlaylist(context, name)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xff2c2c38),
        title = {
            Text("Nueva lista", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Nombre de la lista", color = Color(0x80ffffff)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color(0x50ffffff)
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text("Crear", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color(0x80ffffff))
            }
        }
    )
}

@Composable
fun ListSectionCard(
    title: String,
    subtitle: String,
    icon: Int,
    iconTint: Color = Color(0xFF9c27b0),
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x20ffffff), shape = RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp)
                .background(Color(0x30ffffff), shape = RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(icon), contentDescription = null,
                tint = iconTint, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(subtitle, color = Color(0xffbbbbbb), fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.weight(1f))
        Icon(painterResource(R.drawable.outline_arrow_back_ios_new_24),
            contentDescription = null, tint = Color(0x80ffffff), modifier = Modifier.size(16.dp))
    }
}

@Composable
fun SectionScreen(
    title: String,
    songs: List<Songs>,
    onBack: () -> Unit,
    onSongClick: (List<Songs>, Int) -> Unit,
    showDelete: Boolean = false,
    onDelete: (() -> Unit)? = null
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xff191c1f), Color(0xff2c2c38))))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.outline_arrow_back_ios_new_24),
                    contentDescription = null, tint = Color.White)
            }
            Text(title, color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = 18.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
            if (showDelete && onDelete != null) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(painterResource(R.drawable.outline_delete_24),
                        contentDescription = "Eliminar lista", tint = Color(0xFFe91e63))
                }
            }
        }

        if (songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay canciones aquí", color = Color(0x80ffffff), fontSize = 15.sp)
            }
        } else {
            SongsList(songs = songs, onSongClick = { pos -> onSongClick(songs, pos) })
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Color(0xff2c2c38),
            title = { Text("Eliminar lista", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("¿Seguro que quieres eliminar esta lista?", color = Color(0xffbbbbbb)) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete?.invoke() }) {
                    Text("Eliminar", color = Color(0xFFe91e63), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar", color = Color(0x80ffffff))
                }
            }
        )
    }
}