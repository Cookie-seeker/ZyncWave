package com.example.zyncwave2.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zyncwave2.R
import com.example.zyncwave2.data.EqualizerManager
import com.example.zyncwave2.data.EqualizerPresets

//Paleta
private val EqBackground = Color(0xFF0D0F12)
private val EqPanel = Color(0xFF101319)
private val EqPanelLight = Color(0xFF181B20)
private val EqLine = Color(0x1CFFFFFF)
private val EqAccent = Color(0xFFBBBBBB)
private val EqAccentStrong = Color(0xFFE0E0E0)
private val EqInk = Color(0xFFF7F8F5)
private val EqMuted = Color(0xFF9BA1AA)
private val EqAccentDark = Color(0xFF2C2C2E)

@Composable
fun EqualizerScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("equalizer_prefs", android.content.Context.MODE_PRIVATE)

    val eq = EqualizerManager.equalizer
    val numBands = eq?.numberOfBands?.toInt() ?: 5
    val minLevel = eq?.bandLevelRange?.get(0) ?: -1500
    val maxLevel = eq?.bandLevelRange?.get(1) ?: 1500

    val bandLevels = remember {
        mutableStateListOf(*Array(numBands) {
            prefs.getInt("band_$it", eq?.getBandLevel(it.toShort())?.toInt() ?: 0).toShort()
        })
    }

    var bassBoostStrength by remember {
        mutableStateOf(prefs.getInt("bass_boost", 0).toFloat())
    }
    var virtualizerStrength by remember {
        mutableStateOf(prefs.getInt("virtualizer", 0).toFloat())
    }
    var reverbPreset by remember {
        mutableStateOf(prefs.getInt("reverb_preset", 0))
    }
    var eqEnabled by remember {
        mutableStateOf(prefs.getBoolean("eq_enabled", true))
    }
    // Género activo
    var selectedGenre by remember {
        mutableStateOf(prefs.getString("genre_preset", null))
    }

    LaunchedEffect(Unit) {
        for (i in 0 until numBands) {
            EqualizerManager.setEqualizerBand(i.toShort(), bandLevels[i])
        }
        EqualizerManager.setBassBoost(bassBoostStrength.toInt().toShort())
        EqualizerManager.setVirtualizer(virtualizerStrength.toInt().toShort())
        EqualizerManager.setReverb(reverbPreset.toShort())
    }

    val reverbPresets = listOf("Ninguno", "Sala pequeña", "Sala grande", "Salón", "Cueva")
    val genreNames = EqualizerPresets.presets.keys.toList()

    fun applyGenre(genre: String) {
        val levels = EqualizerPresets.levelsFor(genre, numBands, minLevel, maxLevel)
        levels.forEachIndexed { i, level ->
            bandLevels[i] = level
            EqualizerManager.setEqualizerBand(i.toShort(), level)
        }
        selectedGenre = genre
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EqBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 100.dp)
        ) {
            //Barra superior
            Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp)) {
                // Fila 1 volver + título
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val editor = prefs.edit()
                            for (i in 0 until numBands) {
                                editor.putInt("band_$i", bandLevels[i].toInt())
                            }
                            editor.putInt("bass_boost", bassBoostStrength.toInt())
                            editor.putInt("virtualizer", virtualizerStrength.toInt())
                            editor.putInt("reverb_preset", reverbPreset)
                            editor.putString("genre_preset", selectedGenre)
                            editor.apply()
                            onDismiss()
                        },
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.outline_arrow_back_ios_new_24),
                            contentDescription = null,
                            tint = EqInk
                        )
                    }
                    Text(
                        "Ecualizador",
                        color = EqInk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Fila 2 Restablecer a la derecha
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = eqEnabled,
                            onCheckedChange = { enabled ->
                                eqEnabled = enabled
                                EqualizerManager.setEnabled(enabled, context)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EqAccentDark,
                                checkedTrackColor = EqAccent,
                                uncheckedThumbColor = Color(0xFFEDF0EB),
                                uncheckedTrackColor = Color(0xFF3A4049)
                            )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            if (eqEnabled) "Activado" else "Desactivado",
                            color = EqMuted,
                            fontSize = 13.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            EqualizerManager.resetToDefault(context)
                            for (i in 0 until numBands) { bandLevels[i] = 0 }
                            bassBoostStrength = 0f
                            virtualizerStrength = 0f
                            reverbPreset = 0
                            selectedGenre = null
                        },
                        contentPadding = PaddingValues(
                            horizontal = 16.dp, vertical = 8.dp
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EqInk),
                        border = BorderStroke(1.dp, EqLine)
                    ) {
                        Text("Restablecer", fontSize = 13.sp)
                    }
                }
            }

            //Curva de respuesta
            SectionTitle("Respuesta")
            Spacer(modifier = Modifier.height(8.dp))
            ResponseCurve(
                bandLevels = bandLevels,
                minLevel = minLevel,
                maxLevel = maxLevel,
                enabled = eqEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .alpha(if (eqEnabled) 1f else 0.4f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            //Bandas
            SectionTitle("Bandas")
            Spacer(modifier = Modifier.height(8.dp))
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (eqEnabled) 1f else 0.4f)
                    .background(EqPanelLight, RoundedCornerShape(20.dp))
                    .border(1.dp, EqLine, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                val sliderHeight: Dp = (maxWidth / numBands.coerceAtLeast(1) * 1.5f)
                    .coerceIn(80.dp, 160.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until numBands) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "${bandLevels[i] / 100}",
                                color = EqAccentStrong,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            VerticalSlider(
                                value = bandLevels[i].toFloat(),
                                valueRange = minLevel.toFloat()..maxLevel.toFloat(),
                                onValueChange = { newVal ->
                                    val level = newVal.toInt().toShort()
                                    bandLevels[i] = level
                                    EqualizerManager.setEqualizerBand(i.toShort(), level)
                                    selectedGenre = null
                                },
                                modifier = Modifier.height(sliderHeight),
                                enabled = eqEnabled
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                getBandLabel(eq, i),
                                color = EqMuted,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            //Presets de género
            SectionTitle("Presets de género")
            Spacer(modifier = Modifier.height(8.dp))
            GenreGrid(
                genres = genreNames,
                selected = selectedGenre,
                enabled = eqEnabled,
                onSelect = { applyGenre(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            //Bass Boost
            SectionTitle("Bass Boost")
            Spacer(modifier = Modifier.height(8.dp))
            EffectSlider(
                value = bassBoostStrength,
                onValueChange = {
                    bassBoostStrength = it
                    EqualizerManager.setBassBoost(it.toInt().toShort())
                },
                valueRange = 0f..1000f,
                label = "${bassBoostStrength.toInt() / 10}%",
                enabled = eqEnabled
            )

            Spacer(modifier = Modifier.height(24.dp))

            //Virtualizador
            SectionTitle("Virtualizador / Surround")
            Spacer(modifier = Modifier.height(8.dp))
            EffectSlider(
                value = virtualizerStrength,
                onValueChange = {
                    virtualizerStrength = it
                    EqualizerManager.setVirtualizer(it.toInt().toShort())
                },
                valueRange = 0f..1000f,
                label = "${virtualizerStrength.toInt() / 10}%",
                enabled = eqEnabled
            )

            Spacer(modifier = Modifier.height(24.dp))

            //Reverb
            SectionTitle("Reverb")
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (eqEnabled) 1f else 0.4f)
                    .background(EqPanelLight, RoundedCornerShape(16.dp))
                    .border(1.dp, EqLine, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                reverbPresets.forEachIndexed { index, name ->
                    val active = reverbPreset == index
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(
                                if (active) EqAccent.copy(alpha = 0.10f) else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .then(
                                if (!eqEnabled) Modifier else Modifier.clickable {
                                    reverbPreset = index
                                    EqualizerManager.setReverb(index.toShort())
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .height(10.dp)
                                .width(10.dp)
                                .background(
                                    if (active) EqAccent else Color(0x40FFFFFF),
                                    RoundedCornerShape(50)
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            name,
                            color = if (active) EqAccentStrong else EqInk,
                            fontSize = 15.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        title,
        color = Color(0xFFD6DAE0),
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
    )
}

@Composable
fun EffectSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    label: String,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .background(EqPanelLight, RoundedCornerShape(16.dp))
            .border(1.dp, EqLine, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = EqAccent,
                activeTrackColor = EqAccent,
                inactiveTrackColor = Color(0x40FFFFFF)
            )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, color = EqAccentStrong, fontSize = 13.sp, modifier = Modifier.width(40.dp))
    }
}

@Composable
fun VerticalSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier
                .graphicsLayer { rotationZ = -90f }
                .width(maxHeight),
            colors = SliderDefaults.colors(
                thumbColor = EqAccent,
                activeTrackColor = EqAccent,
                inactiveTrackColor = Color(0x40FFFFFF)
            )
        )
    }
}

// Grid de presets de género

@Composable
private fun GenreGrid(
    genres: List<String>,
    selected: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        genres.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { genre ->
                    val active = genre == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (active) EqAccent else EqPanelLight,
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                1.dp,
                                if (active) EqAccent else EqLine,
                                RoundedCornerShape(12.dp)
                            )
                            .then(
                                if (enabled) Modifier.clickable { onSelect(genre) } else Modifier
                            )
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            genre,
                            color = if (active) EqAccentDark else EqInk,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
                // Rellenar el último renglón si no completa 3 columnas
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

//Curva de respuesta del ecualizador

@Composable
private fun ResponseCurve(
    bandLevels: List<Short>,
    minLevel: Short,
    maxLevel: Short,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(EqPanel, RoundedCornerShape(16.dp))
            .border(1.dp, EqLine, RoundedCornerShape(16.dp))
            .padding(4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val midY = height / 2f
            val range = (maxLevel - minLevel).coerceAtLeast(1)

            // Línea cero
            drawLine(
                color = EqLine,
                start = Offset(0f, midY),
                end = Offset(width, midY),
                strokeWidth = 1f
            )

            if (bandLevels.isEmpty() || !enabled) {
                drawLine(
                    color = EqMuted,
                    start = Offset(0f, midY),
                    end = Offset(width, midY),
                    strokeWidth = 3f
                )
                return@Canvas
            }

            val step = if (bandLevels.size > 1) width / (bandLevels.size - 1) else width
            val points = bandLevels.mapIndexed { index, level ->
                val x = index * step
                val normalized = (level.toFloat() - minLevel) / range // 0..1
                val y = height - normalized * height
                Offset(x, y)
            }

            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val curr = points[i]
                    val midX = (prev.x + curr.x) / 2f
                    quadraticBezierTo(midX, prev.y, curr.x, curr.y)
                }
            }

            drawPath(
                path = path,
                color = EqAccent,
                style = Stroke(width = 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
    }
}

fun getBandLabel(eq: android.media.audiofx.Equalizer?, band: Int): String {
    val freq = eq?.getCenterFreq(band.toShort()) ?: return ""
    return when {
        freq >= 1_000_000 -> "${freq / 1_000_000}k"
        freq >= 1_000     -> "${freq / 1_000}Hz"
        else              -> "${freq}Hz"
    }
}