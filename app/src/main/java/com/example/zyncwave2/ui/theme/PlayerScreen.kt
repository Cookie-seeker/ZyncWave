package com.example.zyncwave2.ui.theme

import android.content.ContentUris
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.zyncwave2.R
import com.example.zyncwave2.data.EqualizerManager
import com.example.zyncwave2.data.FavoritesManager
import com.example.zyncwave2.data.LyricsManager
import com.example.zyncwave2.data.PlayerState
import com.example.zyncwave2.data.PlaylistManager
import com.example.zyncwave2.data.Songs
import com.example.zyncwave2.data.getSongs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Random
import com.example.zyncwave2.ui.theme.BebasNeue
import com.example.zyncwave2.ui.theme.Nunito

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    songsList: List<Songs>,
    initialIndex: Int = 0,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember { PlayerState.exoPlayer!! }
    val scope = rememberCoroutineScope()

    var currentIndex by remember {
        mutableStateOf(PlayerState.currentIndex.value.takeIf { it >= 0 } ?: initialIndex)
    }



    var isShuffle by rememberSaveable { mutableStateOf(false) }
    var isRepeat by rememberSaveable { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var elapsed by remember { mutableLongStateOf(exoPlayer.currentPosition) }
    var duration by remember { mutableLongStateOf(exoPlayer.duration.coerceAtLeast(0L)) }
    var shuffledList by remember { mutableStateOf(songsList) }
    var showMenu by remember { mutableStateOf(false) }
    var showLyricsEditor by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var currentLyrics by remember { mutableStateOf("") }
    var isFavorite by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showTagEditor by remember { mutableStateOf(false) }
    var imageVersion by remember { mutableStateOf(0) }

    val waveform = remember { getWaveform() }
    var waveformProgress by remember { mutableFloatStateOf(0f) }

    val currentSong by remember { PlayerState.currentSong }
    var showEqualizer by remember { mutableStateOf(false) }

    // Animación de rotación
    val rotationAnim = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            rotationAnim.animateTo(
                targetValue = rotationAnim.value + 36000f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 8000 * 100,
                        easing = LinearEasing
                    )
                )
            )
        }
        // Al pausar se detiene en la posición actual
    }

    LaunchedEffect(Unit) {
        isPlaying = exoPlayer.isPlaying
        elapsed = exoPlayer.currentPosition
        duration = exoPlayer.duration.coerceAtLeast(0L)
        waveformProgress = if (duration > 0) elapsed.toFloat() / duration else 0f
    }

    LaunchedEffect(isPlaying) {
        PlayerState.isPlaying.value = isPlaying
    }

    LaunchedEffect(currentIndex, isShuffle) {
        val list = if (isShuffle) shuffledList else songsList
        val song = list.getOrNull(currentIndex) ?: return@LaunchedEffect
        currentLyrics = LyricsManager.loadLyrics(context, song.id)
        isFavorite = FavoritesManager.isFavorite(song.id)
        showLyrics = false

        val alreadyPlayingThis = exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString() == song.data
        if (!alreadyPlayingThis) {
            val artUri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                song.albumId
            )
            val mediaItem = MediaItem.Builder()
                .setUri(song.data)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artists)
                        .setAlbumTitle(song.albumName)
                        .setArtworkUri(artUri)
                        .build()
                )
                .build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
            isPlaying = true
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlay: Boolean) {
                isPlaying = isPlay
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    duration = exoPlayer.duration
                }
                if (playbackState == Player.STATE_ENDED) {
                    val list = if (isShuffle) shuffledList else songsList
                    val nextIndex = (currentIndex + 1) % list.size
                    currentIndex = nextIndex
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    val list = if (isShuffle) shuffledList else songsList
                    currentIndex = (currentIndex + 1) % list.size
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            elapsed = exoPlayer.currentPosition
            waveformProgress = if (duration > 0) elapsed.toFloat() / duration else 0f

            // Guardar posición cada 5 segundos
            if (elapsed > 0) {
                PlayerState.saveLastSession(context, elapsed)
            }
            delay(500)
        }
    }

    LaunchedEffect(currentIndex) {
        val list = if (isShuffle) shuffledList else songsList
        PlayerState.currentSong.value = list.getOrNull(currentIndex)
        PlayerState.currentIndex.value = currentIndex
        PlayerState.songsList.value = songsList
        isFavorite = list.getOrNull(currentIndex)?.let {
            FavoritesManager.isFavorite(it.id)
        } ?: false
        PlayerState.saveLastSession(context, 0L)
    }

    LaunchedEffect(Unit) {
        val audioSessionId = exoPlayer.audioSessionId
        if (audioSessionId != 0) {
            EqualizerManager.init(audioSessionId)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xff191c1f), Color(0xff2c2c38))
                )
            )
    ) {
        currentSong?.let { song ->

            val artBitmap = remember(song.data, imageVersion) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(song.data)
                    val art = retriever.embeddedPicture
                    retriever.release()
                    if (art != null) BitmapFactory.decodeByteArray(art, 0, art.size)
                    else null
                } catch (e: Exception) { null }
            }

            // Fondo blur
            if (artBitmap != null) {
                Image(
                    bitmap = artBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(18.dp),
                    contentScale = ContentScale.Crop,
                    alpha = 0.40f
                )
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            song.albumId
                        )).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(18.dp),
                    contentScale = ContentScale.Crop,
                    alpha = 0.40f,
                    error = painterResource(R.drawable.baseline_music_note_24),
                    fallback = painterResource(R.drawable.baseline_music_note_24)
                )
            }



            // Menú tres puntos
            Row(Modifier.padding(horizontal = 16.dp, vertical = 48.dp)) {

                Spacer(modifier = Modifier.weight(1f))
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .size(25.dp)

                    ) {
                        Icon(
                            painterResource(R.drawable.outline_more_horiz_24),
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Color(0xff2c2c38))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Editar letras", color = Color.White) },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.outline_edit_note_24),
                                    contentDescription = null, tint = Color.White)
                            },
                            onClick = { showMenu = false; showLyricsEditor = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Agregar a lista", color = Color.White) },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.outline_playlist_add_24),
                                    contentDescription = null, tint = Color.White)
                            },
                            onClick = { showMenu = false; showAddToPlaylist = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Editar etiquetas", color = Color.White) },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.outline_edit_24),
                                    contentDescription = null, tint = Color.White)
                            },
                            onClick = { showMenu = false; showTagEditor = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Ecualizador", color = Color.White) },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.outline_equalizer_24),
                                    contentDescription = null, tint = Color.White)
                            },
                            onClick = { showMenu = false; showEqualizer = true }
                        )
                    }
                }
            }

            // Carátula + título + artista
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 90.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!showLyrics) {
                    if (artBitmap != null) {
                        Image(
                            bitmap = artBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(320.dp)
                                .rotate(rotationAnim.value)
                                .clip(CircleShape)
                                .background(Color(0x30ffffff), shape = CircleShape)
                                .clickable { showLyrics = true },
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        AsyncImage(
                            model = ContentUris.withAppendedId(
                                Uri.parse("content://media/external/audio/albumart"),
                                song.albumId
                            ),
                            contentDescription = null,
                            modifier = Modifier
                                .size(320.dp)
                                .rotate(rotationAnim.value)
                                .clip(CircleShape)
                                .background(Color(0x30ffffff), shape = CircleShape)
                                .clickable { showLyrics = true },
                            contentScale = ContentScale.Crop,
                            error = painterResource(R.drawable.baseline_music_note_24),
                            placeholder = painterResource(R.drawable.baseline_music_note_24)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(320.dp)
                            .background(Color(0x40000000))
                            .clickable { showLyrics = false },
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentLyrics.isNotBlank()) {
                            val scrollState = rememberScrollState()
                            Text(
                                text = currentLyrics,
                                color = Color.White,
                                fontSize = 18.sp,
                                lineHeight = 25.sp,
                                textAlign = TextAlign.Left,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .padding(24.dp)
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    painterResource(R.drawable.outline_edit_note_24),
                                    contentDescription = null,
                                    tint = Color(0x80ffffff),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Sin letras\nToca ··· para agregar",
                                    color = Color(0x80ffffff),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                    // Título con marquee
                Text(
                    song.title.orEmpty(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    fontFamily = BebasNeue,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(top = 32.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .basicMarquee(iterations = Int.MAX_VALUE)
                )

                    // Artista con marquee
                Text(
                    song.artists.orEmpty(),
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    fontFamily = Nunito,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .basicMarquee(iterations = Int.MAX_VALUE)
                )
            }

            // Controles
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 125.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                WaveformBar(
                    values = waveform,
                    progress = waveformProgress,
                    modifier = Modifier.fillMaxWidth().height(70.dp)
                ) { percent ->
                    val seek = (percent * duration).toLong()
                    exoPlayer.seekTo(seek)
                    elapsed = seek
                    waveformProgress = percent
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime((elapsed / 1000).toInt()), color = Color.White, fontSize = 13.sp)
                    Text(formatTime((duration / 1000).toInt()), color = Color.White, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showEqualizer = true }) {
                        Icon(
                            painterResource(R.drawable.outline_equalizer_24),
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = {
                        isRepeat = !isRepeat
                        exoPlayer.repeatMode =
                            if (isRepeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    }) {
                        Icon(painterResource(R.drawable.outline_repeat_one_24),
                            contentDescription = null,
                            tint = if (isRepeat) Color.Black else Color.White)
                    }
                    IconButton(onClick = {
                        val list = if (isShuffle) shuffledList else songsList
                        currentIndex = if (currentIndex - 1 < 0) list.size - 1 else currentIndex - 1
                    }) {
                        Icon(painterResource(R.drawable.outline_skip_previous_24),
                            contentDescription = null, tint = Color.White)
                    }
                    IconButton(onClick = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    }) {
                        Icon(
                            painterResource(if (isPlaying) R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24),
                            contentDescription = null, tint = Color.White
                        )
                    }
                    IconButton(onClick = {
                        val list = if (isShuffle) shuffledList else songsList
                        currentIndex = (currentIndex + 1) % list.size
                    }) {
                        Icon(painterResource(R.drawable.outline_skip_next_24),
                            contentDescription = null, tint = Color.White)
                    }
                    IconButton(onClick = {
                        isShuffle = !isShuffle
                        if (isShuffle) {
                            shuffledList = songsList.shuffled()
                            currentIndex = 0
                        } else {
                            val currentSongData = PlayerState.currentSong.value?.data
                            val originalIndex = songsList.indexOfFirst { it.data == currentSongData }
                            currentIndex = if (originalIndex >= 0) originalIndex else 0
                        }
                    }) {
                        Icon(
                            painterResource(R.drawable.outline_shuffle_24),
                            contentDescription = null,
                            tint = if (isShuffle) Color.Black else Color.White
                        )
                    }
                    IconButton(onClick = {
                        val s = PlayerState.currentSong.value ?: return@IconButton
                        FavoritesManager.toggleFavorite(context, s.id)
                        isFavorite = FavoritesManager.isFavorite(s.id)
                    }) {
                        Icon(
                            painter = painterResource(
                                if (isFavorite) R.drawable.baseline_favorite_24
                                else R.drawable.outline_favorite_24
                            ),
                            contentDescription = null,
                            tint = if (isFavorite) Color(0xFFe91e63) else Color.White
                        )
                    }
                }
            }
        }

        if (showLyricsEditor) {
            currentSong?.let { song ->
                LyricsEditorScreen(
                    song = song,
                    onDismiss = { showLyricsEditor = false },
                    onSave = { lyrics ->
                        currentLyrics = lyrics
                        showLyricsEditor = false
                    }
                )
            }
        }

        if (showAddToPlaylist) {
            currentSong?.let { song ->
                AddToPlaylistDialog(
                    song = song,
                    onDismiss = { showAddToPlaylist = false }
                )
            }
        }

        if (showTagEditor) {
            currentSong?.let { song ->
                TagEditorScreen(
                    song = song,
                    onDismiss = { showTagEditor = false },
                    onSaved = {
                        showTagEditor = false
                        imageVersion++
                        scope.launch {
                            delay(1500)
                            val updatedSongs = withContext(Dispatchers.IO) {
                                getSongs(context, PlayerState.selectedFolders.value)
                            }
                            val updatedSong = updatedSongs.find { it.id == song.id }
                            updatedSong?.let {
                                PlayerState.currentSong.value = it
                                PlayerState.songsList.value = updatedSongs
                            }
                        }
                    }
                )
            }
        }

        if (showEqualizer) {
            EqualizerScreen(onDismiss = { showEqualizer = false })
        }
    }
}

@Composable
fun AddToPlaylistDialog(
    song: Songs,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val playlists = PlaylistManager.playlists

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xff2c2c38),
        title = {
            Text("Agregar a lista", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            if (playlists.isEmpty()) {
                Text(
                    "No tienes listas creadas.\nVe a Listas > + para crear una.",
                    color = Color(0x80ffffff),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LazyColumn {
                    items(playlists) { playlist ->
                        val alreadyAdded = playlist.songIds.contains(song.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !alreadyAdded) {
                                    PlaylistManager.addSongToPlaylist(context, playlist.id, song.id)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painterResource(R.drawable.outline_queue_music_24),
                                contentDescription = null,
                                tint = if (alreadyAdded) Color(0x50ffffff) else Color(0xFF9c27b0),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    playlist.name,
                                    color = if (alreadyAdded) Color(0x80ffffff) else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                if (alreadyAdded) {
                                    Text("Ya está en esta lista", color = Color(0x60ffffff), fontSize = 12.sp)
                                }
                            }
                        }
                        HorizontalDivider(color = Color(0x20ffffff))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color(0x80ffffff))
            }
        }
    )
}

fun getWaveform(): IntArray {
    val random = Random(System.currentTimeMillis())
    return IntArray(size = 50) { 5 + random.nextInt(50) }
}

fun formatTime(seconds: Int): String = String.format("%02d:%02d", seconds / 60, seconds % 60)
