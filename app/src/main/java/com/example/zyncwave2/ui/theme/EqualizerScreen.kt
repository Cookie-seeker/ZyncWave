package com.example.zyncwave2.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zyncwave2.R
import com.example.zyncwave2.data.EqualizerManager

@Composable
fun EqualizerScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("equalizer_prefs", android.content.Context.MODE_PRIVATE)

    val eq = EqualizerManager.equalizer
    val numBands = eq?.numberOfBands?.toInt() ?: 5
    val minLevel = eq?.bandLevelRange?.get(0) ?: -1500
    val maxLevel = eq?.bandLevelRange?.get(1) ?: 1500

    // Cargar valores guardados
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

    // Aplicar valores cargados al iniciar
    LaunchedEffect(Unit) {
        for (i in 0 until numBands) {
            EqualizerManager.setEqualizerBand(i.toShort(), bandLevels[i])
        }
        EqualizerManager.setBassBoost(bassBoostStrength.toInt().toShort())
        EqualizerManager.setVirtualizer(virtualizerStrength.toInt().toShort())
        EqualizerManager.setReverb(reverbPreset.toShort())
    }

    val reverbPresets = listOf("Ninguno", "Sala pequeña", "Sala grande", "Salón", "Cueva")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xff191c1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 100.dp)
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    // Guardar antes de salir
                    val editor = prefs.edit()
                    for (i in 0 until numBands) {
                        editor.putInt("band_$i", bandLevels[i].toInt())
                    }
                    editor.putInt("bass_boost", bassBoostStrength.toInt())
                    editor.putInt("virtualizer", virtualizerStrength.toInt())
                    editor.putInt("reverb_preset", reverbPreset)
                    editor.apply()
                    onDismiss()
                }) {
                    Icon(
                        painterResource(R.drawable.outline_arrow_back_ios_new_24),
                        contentDescription = null,
                        tint = Color.White
                    )
                }
                Text(
                    "Ecualizador",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f)
                )
                // Botón restablecer
                TextButton(onClick = {
                    EqualizerManager.resetToDefault(context)
                    for (i in 0 until numBands) {
                        bandLevels[i] = 0
                    }
                    bassBoostStrength = 0f
                    virtualizerStrength = 0f
                    reverbPreset = 0
                }) {
                    Text(
                        "Restablecer",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }

            // Ecualizador
            SectionTitle("Ecualizador")
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x20ffffff), RoundedCornerShape(16.dp))
                    .padding(16.dp),
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
                            color = Color.White,
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
                            },
                            modifier = Modifier.height(150.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            getBandLabel(eq, i),
                            color = Color(0xffbbbbbb),
                            fontSize = 9.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bass Boost
            SectionTitle("Bass Boost")
            Spacer(modifier = Modifier.height(8.dp))
            EffectSlider(
                value = bassBoostStrength,
                onValueChange = {
                    bassBoostStrength = it
                    EqualizerManager.setBassBoost(it.toInt().toShort())
                },
                valueRange = 0f..1000f,
                label = "${bassBoostStrength.toInt() / 10}%"
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Virtualizador
            SectionTitle("Virtualizador / Surround")
            Spacer(modifier = Modifier.height(8.dp))
            EffectSlider(
                value = virtualizerStrength,
                onValueChange = {
                    virtualizerStrength = it
                    EqualizerManager.setVirtualizer(it.toInt().toShort())
                },
                valueRange = 0f..1000f,
                label = "${virtualizerStrength.toInt() / 10}%"
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Reverb
            SectionTitle("Reverb")
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x20ffffff), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                reverbPresets.forEachIndexed { index, name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = reverbPreset == index,
                            onClick = {
                                reverbPreset = index
                                EqualizerManager.setReverb(index.toShort())
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF2C2C2E),   // ← negro carbón
                                unselectedColor = Color(0x80ffffff)
                            )
                        )
                        Text(name, color = Color.White, fontSize = 15.sp)
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
        color = Color(0xFFE0E0E0),           // ← gris claro elegante (visible sobre fondo oscuro)
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp
    )
}

@Composable
fun EffectSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    label: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x20ffffff), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF2C2C2E),          // ← negro carbón
                activeTrackColor = Color(0xFF2C2C2E),    // ← negro carbón
                inactiveTrackColor = Color(0x40ffffff)
            )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.width(36.dp))
    }
}

@Composable
fun VerticalSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier
                .graphicsLayer { rotationZ = -90f }
                .width(150.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF2C2C2E),          // ← negro carbón
                activeTrackColor = Color(0xFF2C2C2E),    // ← negro carbón
                inactiveTrackColor = Color(0x40ffffff)
            )
        )
    }
}

fun getBandLabel(eq: android.media.audiofx.Equalizer?, band: Int): String {
    val freq = eq?.getCenterFreq(band.toShort()) ?: return ""
    return when {
        freq >= 1_000_000 -> "${freq / 1_000_000}k"
        freq >= 1_000 -> "${freq / 1_000}Hz"
        else -> "${freq}Hz"
    }
}