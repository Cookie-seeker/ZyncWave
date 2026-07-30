package com.example.zyncwave2.ui.theme

import android.app.Activity
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.zyncwave2.R
import com.example.zyncwave2.data.LrcParser
import com.example.zyncwave2.data.PlayerState
import com.example.zyncwave2.data.PlaylistManager
import com.example.zyncwave2.data.RepeatMode
import com.example.zyncwave2.data.Songs
import com.example.zyncwave2.presentation.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    songsList: List<Songs>,
    initialIndex: Int = 0,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val s by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    var shuffleMessage by remember { mutableStateOf<String?>(null) }
    var repeatMessage by remember { mutableStateOf<String?>(null) }

    val rotationAnim = remember { Animatable(0f) }
    LaunchedEffect(s.isPlaying) {
        if (s.isPlaying) {
            rotationAnim.animateTo(
                targetValue = rotationAnim.value + 36000f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 8000 * 100, easing = LinearEasing)
                )
            )
        }
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = if (PlayerState.isQueueExpanded) SheetValue.Expanded else SheetValue.PartiallyExpanded,
            skipHiddenState = true
        )
    )

    val listState = rememberLazyListState()
    LaunchedEffect(s.currentIndex) {
        if (s.currentIndex > 0) {
            listState.animateScrollToItem((s.currentIndex - 1).coerceAtLeast(0))
        }
    }

    LaunchedEffect(s.showQueue) {
        if (s.showQueue) {
            scaffoldState.bottomSheetState.expand()
        } else {
            scaffoldState.bottomSheetState.partialExpand()
        }
    }

    LaunchedEffect(scaffoldState.bottomSheetState.currentValue) {
        val isExpanded = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded
        PlayerState.isQueueExpanded = isExpanded
        viewModel.setShowQueue(isExpanded)
    }

    BackHandler(
        enabled = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded ||
                scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded
    ) {
        scope.launch {
            scaffoldState.bottomSheetState.partialExpand()
        }
    }


    val nextIndex = (s.currentIndex + 1) % s.activeList.size.coerceAtLeast(1)
    val nextSong  = s.activeList.getOrNull(nextIndex)

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 80.dp,
        sheetContainerColor = Color(0xff1e1e28),
        sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        sheetDragHandle = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(3.dp)
                            .background(Color(0x50ffffff), RoundedCornerShape(2.dp))
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Reproduciendo desde",
                            color = Color(0x80ffffff),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        )
                        Text(
                            when (s.queueSource) {
                                PlayerState.QueueSource.ALL_SONGS  -> "Canciones"
                                PlayerState.QueueSource.FAVORITES  -> "Favoritos"
                                PlayerState.QueueSource.RECENT     -> "Agregadas recientemente"
                                PlayerState.QueueSource.PLAYLIST   -> {
                                    val plId = s.queueSourceId.toLongOrNull()
                                    PlaylistManager.playlists.find { it.id == plId }?.name ?: "Lista"
                                }
                                PlayerState.QueueSource.ALBUM      -> s.queueSourceId
                                PlayerState.QueueSource.ARTIST     -> s.queueSourceId
                                PlayerState.QueueSource.FOLDER     -> java.io.File(s.queueSourceId).name
                            },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (nextSong != null && nextSong != s.currentSong) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0x18ffffff))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                painterResource(R.drawable.outline_skip_next_24),
                                contentDescription = null,
                                tint = Color(0xCCffffff),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text(
                                    nextSong.title.orEmpty(),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(120.dp)
                                )
                                Text(
                                    nextSong.artists.orEmpty(),
                                    color = Color(0x80ffffff),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(120.dp)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = Color(0x15ffffff))
            }
        },
        sheetContent = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Cola de reproducción",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    "${s.activeList.size} canciones",
                    color = Color(0x60ffffff),
                    fontSize = 12.sp
                )
            }
            HorizontalDivider(color = Color(0x12ffffff))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                itemsIndexed(s.activeList) { index, queueSong ->
                    val isActive = index == s.currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isActive) Color(0x18ffffff) else Color.Transparent)
                            .clickable { viewModel.playFromQueue(index) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x20ffffff)),
                            contentAlignment = Alignment.Center
                        ) {
                            SongArtImage(
                                data     = queueSong.data,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(6.dp)),
                                errorRes = R.drawable.baseline_music_note_24
                            )
                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0x88000000), RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painterResource(
                                            if (s.isPlaying) R.drawable.outline_equalizer_24
                                            else R.drawable.baseline_play_arrow_24
                                        ),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                queueSong.title.orEmpty(),
                                color = if (isActive) Color.White else Color(0xDDffffff),
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                queueSong.artists.orEmpty(),
                                color = Color(0x70ffffff),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isActive) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(3.dp, 36.dp)
                                    .background(Color(0xFFe91e63), RoundedCornerShape(2.dp))
                            )
                        }
                    }
                    HorizontalDivider(color = Color(0x08ffffff))
                }
            }
        }
    ) {

        // ── Contenido principal del player ────────────────────────────────────
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xff191c1f), Color(0xff2c2c38))))
        ) {
            val song = s.currentSong

            if (song != null) {

                // ── Fondo blur ────────────────────────────────────────────────
                var artBitmap by remember(song.data, s.imageVersion) {
                    mutableStateOf<Bitmap?>(null)
                }

                LaunchedEffect(song.data, s.imageVersion) {
                    artBitmap = withContext(Dispatchers.IO) {
                        try {
                            val retriever = MediaMetadataRetriever()
                            retriever.setDataSource(song.data)
                            val art = retriever.embeddedPicture
                            retriever.release()
                            if (art != null) BitmapFactory.decodeByteArray(art, 0, art.size) else null
                        } catch (e: Exception) { null }
                    }
                }

                val currentBitmap = artBitmap
                if (currentBitmap != null) {
                    Image(
                        bitmap = currentBitmap.asImageBitmap(),
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

                // ── Layout responsive ─────────────────────────────────────────
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val screenWidth  = maxWidth
                    val screenHeight = maxHeight
                    val systemBars   = WindowInsets.systemBars.asPaddingValues()
                    val topInset     = systemBars.calculateTopPadding()
                    val bottomInset  = systemBars.calculateBottomPadding()

                    // Espacio real disponible (sin barras del sistema ni el peek del BottomSheet)
                    val availableHeight = screenHeight - topInset - bottomInset - 80.dp

                    // Imagen: el menor entre 72% del ancho y 42% del alto disponible
                    val imageSize = minOf(screenWidth * 0.72f, availableHeight * 0.42f)

                    // ── Bloque de controles anclado al fondo ──────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(
                                start  = 24.dp,
                                end    = 24.dp,
                                bottom = bottomInset + 75.dp  // justo encima del BottomSheet peek
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val waveform = remember(s.currentSong?.id) { getWaveform() }

                        WaveformBar(
                            values   = waveform,
                            progress = s.waveformProgress,
                            modifier = Modifier.fillMaxWidth().height(70.dp)
                        ) { percent -> viewModel.seekTo((percent * s.duration).toLong()) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                formatTime((s.elapsed / 1000).toInt()),
                                color = Color(0x80ffffff), fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            )
                            Text(
                                formatTime((s.duration / 1000).toInt()),
                                color = Color(0x80ffffff), fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // Fila 1: botones secundarios
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.setShowEqualizer(true) }) {
                                Icon(painterResource(R.drawable.outline_equalizer_24), null, tint = Color.White)
                            }
                            IconButton(onClick = {
                                viewModel.toggleRepeatMode()
                                repeatMessage = when (s.repeatMode) {
                                    RepeatMode.NONE -> "Repetir canción"
                                    RepeatMode.ONE  -> "Repetir cola"
                                    RepeatMode.ALL  -> "Repetir desactivado"
                                }
                            }) {
                                Icon(
                                    painterResource(
                                        when (s.repeatMode) {
                                            RepeatMode.NONE, RepeatMode.ALL -> R.drawable.outline_repeat_24
                                            RepeatMode.ONE -> R.drawable.outline_repeat_one_24
                                        }
                                    ),
                                    contentDescription = null,
                                    tint = when (s.repeatMode) {
                                        RepeatMode.NONE -> Color.White
                                        RepeatMode.ONE, RepeatMode.ALL -> Color.Black
                                    }
                                )
                            }
                            IconButton(onClick = {
                                viewModel.toggleShuffle()
                                shuffleMessage = if (s.isShuffle) "Modo aleatorio desactivado" else "Modo aleatorio activado"
                            }) {
                                Icon(
                                    painterResource(R.drawable.outline_shuffle_24),
                                    contentDescription = null,
                                    tint = if (s.isShuffle) Color.Black else Color.White
                                )
                            }
                            IconButton(onClick = { viewModel.toggleFavorite() }) {
                                Icon(
                                    painterResource(
                                        if (s.isFavorite) R.drawable.baseline_favorite_24
                                        else R.drawable.outline_favorite_24
                                    ),
                                    contentDescription = null,
                                    tint = if (s.isFavorite) Color(0xFFe91e63) else Color.White
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        // Fila 2: botones principales
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.prev() }) {
                                Icon(
                                    painterResource(R.drawable.outline_skip_previous_24),
                                    null, tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.togglePlaying() },
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(
                                    painterResource(
                                        if (s.isPlaying) R.drawable.baseline_pause_24
                                        else R.drawable.baseline_play_arrow_24
                                    ),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(56.dp)
                                )
                            }
                            IconButton(onClick = { viewModel.next() }) {
                                Icon(
                                    painterResource(R.drawable.outline_skip_next_24),
                                    null, tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }

                    // ── Bloque superior: menú + carátula + título + artista ───
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(
                                top        = topInset + 12.dp,
                                start      = 24.dp,
                                end        = 24.dp,
                                // deja espacio para el bloque de controles abajo
                                bottom     = availableHeight * 0.48f
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Botón ···
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Box {
                                IconButton(
                                    onClick = { viewModel.setShowMenu(true) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        painterResource(R.drawable.outline_more_horiz_24),
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                                DropdownMenu(
                                    expanded = s.showMenu,
                                    onDismissRequest = { viewModel.setShowMenu(false) },
                                    modifier = Modifier.background(Color(0xff2c2c38))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Editar letras", color = Color.White) },
                                        leadingIcon = { Icon(painterResource(R.drawable.outline_edit_note_24), null, tint = Color.White) },
                                        onClick = { viewModel.setShowMenu(false); viewModel.setShowLyricsEditor(true) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Agregar a lista", color = Color.White) },
                                        leadingIcon = { Icon(painterResource(R.drawable.outline_playlist_add_24), null, tint = Color.White) },
                                        onClick = { viewModel.setShowMenu(false); viewModel.setShowAddToPlaylist(true) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Editar etiquetas", color = Color.White) },
                                        leadingIcon = { Icon(painterResource(R.drawable.outline_edit_24), null, tint = Color.White) },
                                        onClick = {
                                            viewModel.setShowMenu(false)
                                            when {
                                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                                                    if (!android.os.Environment.isExternalStorageManager()) {
                                                        val intent = android.content.Intent(
                                                            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                                            android.net.Uri.parse("package:${context.packageName}")
                                                        )
                                                        (context as Activity).startActivity(intent)
                                                    }
                                                }
                                                else -> {
                                                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                                                        context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                                    if (!granted) {
                                                        ActivityCompat.requestPermissions(
                                                            context as Activity,
                                                            arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                                            1001
                                                        )
                                                    }
                                                }
                                            }
                                            viewModel.setShowTagEditor(true)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Ecualizador", color = Color.White) },
                                        leadingIcon = { Icon(painterResource(R.drawable.outline_equalizer_24), null, tint = Color.White) },
                                        onClick = { viewModel.setShowMenu(false); viewModel.setShowEqualizer(true) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Escanear biblioteca", color = Color.White) },
                                        leadingIcon = { Icon(painterResource(R.drawable.outline_refresh_24), null, tint = Color.White) },
                                        onClick = { viewModel.setShowMenu(false); viewModel.rescanLibrary() }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Carátula / Letras
                        if (!s.showLyrics) {
                            val currentBitmap = artBitmap
                            if (currentBitmap != null) {
                                Image(
                                    bitmap = currentBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(imageSize)
                                        .rotate(rotationAnim.value)
                                        .clip(CircleShape)
                                        .background(Color(0x30ffffff), shape = CircleShape)
                                        .clickable { viewModel.setShowLyrics(true) },
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
                                        .size(imageSize)
                                        .rotate(rotationAnim.value)
                                        .clip(CircleShape)
                                        .background(Color(0x30ffffff), shape = CircleShape)
                                        .clickable { viewModel.setShowLyrics(true) },
                                    contentScale = ContentScale.Crop,
                                    error = painterResource(R.drawable.baseline_music_note_24),
                                    placeholder = painterResource(R.drawable.baseline_music_note_24)
                                )
                            }
                        } else {
                            Box(modifier = Modifier.size(imageSize)) {
                                Box(modifier = Modifier.fillMaxSize().background(Color(0x40000000)))
                                if (s.currentLyrics.isNotBlank()) {
                                    SyncedLyricsView(
                                        lyrics    = s.currentLyrics,
                                        elapsedMs = s.elapsed,
                                        onSeek    = { ms -> viewModel.seekTo(ms) },
                                        modifier  = Modifier.fillMaxSize().padding(top = 32.dp)
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(top = 32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(painterResource(R.drawable.outline_edit_note_24), null,
                                            tint = Color(0x80ffffff), modifier = Modifier.size(40.dp))
                                        Spacer(Modifier.height(8.dp))
                                        Text("Sin letras\nToca ··· para agregar",
                                            color = Color(0x80ffffff), fontSize = 12.sp,
                                            textAlign = TextAlign.Center)
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.setShowLyrics(false) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(38.dp)
                                ) {
                                    Icon(
                                        painterResource(R.drawable.outline_close_24),
                                        contentDescription = "Cerrar letras",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        // Título y artista
                        Text(
                            song.title.orEmpty(),
                            fontWeight = FontWeight.Bold, fontSize = 26.sp,
                            textAlign = TextAlign.Center, color = Color.White,
                            fontFamily = BebasNeue, maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(iterations = Int.MAX_VALUE)
                        )
                        Text(
                            song.artists.orEmpty(),
                            fontWeight = FontWeight.Normal, fontSize = 16.sp,
                            textAlign = TextAlign.Center, color = Color.White,
                            fontFamily = Nunito, maxLines = 1,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .fillMaxWidth()
                                .basicMarquee(iterations = Int.MAX_VALUE)
                        )
                    }
                } // fin BoxWithConstraints

            } // fin if (song != null)

            // Dialogs
            if (s.showLyricsEditor && s.currentSong != null) {
                LyricsEditorScreen(
                    song      = s.currentSong!!,
                    onDismiss = { viewModel.setShowLyricsEditor(false) },
                    onSave    = { lyrics -> viewModel.saveLyrics(lyrics) }
                )
            }
            if (s.showAddToPlaylist && s.currentSong != null) {
                AddToPlaylistDialog(
                    song      = s.currentSong!!,
                    onDismiss = { viewModel.setShowAddToPlaylist(false) }
                )
            }
            if (s.showTagEditor && s.currentSong != null) {
                TagEditorScreen(
                    song      = s.currentSong!!,
                    onDismiss = { viewModel.setShowTagEditor(false) },
                    onSaved   = { newTitle, newArtist, newAlbum, newGenre, newTrack, newDisc ->
                        viewModel.onTagsSaved(newTitle, newArtist, newAlbum, newGenre, newTrack, newDisc)
                    },
                    playerViewModel = viewModel
                )
            }
            if (s.showEqualizer) {
                EqualizerScreen(onDismiss = { viewModel.setShowEqualizer(false) })
            }

            repeatMessage?.let { msg ->
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(2000)
                    repeatMessage = null
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp)
                        .background(Color(0xDD1e1e2e), RoundedCornerShape(24.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(8.dp))
                        Text(msg, color = Color.White, fontSize = 14.sp)
                    }
                }
            }

            shuffleMessage?.let { msg ->
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(2000)
                    shuffleMessage = null
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp)
                        .background(Color(0xDD1e1e2e), RoundedCornerShape(24.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(8.dp))
                        Text(msg, color = Color.White, fontSize = 14.sp)
                    }
                }
            }

            if (s.queueEndedEvent) {
                LaunchedEffect(s.queueEndedEvent) {
                    kotlinx.coroutines.delay(3000)
                    viewModel.consumeQueueEndedEvent()
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp)
                        .background(Color(0xDD1e1e2e), RoundedCornerShape(24.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(8.dp))
                        Text("Se alcanzó el final de la cola", color = Color.White, fontSize = 14.sp)
                    }
                }
            }

        } // fin Box principal
    } // fin BottomSheetScaffold
}

// ── SyncedLyricsView ──────────────────────────────────────────────────────────

@Composable
fun SyncedLyricsView(
    lyrics: String,
    elapsedMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val isLrc = remember(lyrics) { LrcParser.isLrc(lyrics) }

    if (isLrc) {
        val lines = remember(lyrics) { LrcParser.parse(lyrics) }
        val activeIndex by remember(elapsedMs) {
            derivedStateOf { LrcParser.activeIndex(lines, elapsedMs) }
        }
        val listState = rememberLazyListState()

        LaunchedEffect(activeIndex) {
            if (activeIndex >= 0) {
                listState.animateScrollToItem(
                    index = (activeIndex - 1).coerceAtLeast(0)
                )
            }
        }

        LazyColumn(
            state    = listState,
            modifier = modifier.padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(lines) { index, line ->
                val isActive = index == activeIndex
                Text(
                    text       = line.text,
                    color      = if (isActive) Color.White else Color.White.copy(alpha = 0.45f),
                    fontSize   = if (isActive) 16.sp else 14.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    lineHeight = 22.sp,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .clickable { onSeek(line.timeMs) }
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                )
            }
        }
    } else {
        Text(
            text       = lyrics,
            color      = Color.White,
            fontSize   = 15.sp,
            lineHeight = 24.sp,
            textAlign  = TextAlign.Left,
            modifier   = modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        )
    }
}

// ── AddToPlaylistDialog ───────────────────────────────────────────────────────

@Composable
fun AddToPlaylistDialog(song: Songs, onDismiss: () -> Unit) {
    val context   = LocalContext.current
    val playlists = PlaylistManager.playlists.toList()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xff2c2c38),
        title = { Text("Agregar a lista", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            if (playlists.isEmpty()) {
                Text(
                    "No tienes listas creadas.\nVe a Listas > + para crear una.",
                    color = Color(0x80ffffff), fontSize = 14.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
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
                                tint = if (alreadyAdded) Color(0x50ffffff) else Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    playlist.name,
                                    color = if (alreadyAdded) Color(0x80ffffff) else Color.White,
                                    fontWeight = FontWeight.Bold, fontSize = 15.sp
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
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Color(0x80ffffff)) }
        }
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

fun formatTime(seconds: Int): String =
    String.format("%02d:%02d", seconds / 60, seconds % 60)

fun getWaveform(): IntArray {
    val random = java.util.Random(System.currentTimeMillis())
    return IntArray(size = 50) { 5 + random.nextInt(50) }
}
