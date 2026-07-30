package com.example.zyncwave2.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp

@Composable
fun WaveformBar(
    values: IntArray,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    onSeek: ((Float) -> Unit)? = null
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeek?.invoke((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .background(Color.Transparent)
    ) {
        val verticalPadding = 4.dp
        // Altura real disponible para las barras (lo que el padre nos dio menos padding)
        val usableHeight = (maxHeight - verticalPadding * 2).coerceAtLeast(4.dp)

        val maxVal = values.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f
        val bars   = values.size

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            values.forEachIndexed { index, v ->
                val isPlayed  = index < (bars * progress).toInt()
                val barColor  = if (isPlayed) Color.Black else Color.Black.copy(alpha = 0.2f)

                val barHeight = (usableHeight * (v.toFloat() / maxVal)).coerceAtLeast(4.dp)

                Box(
                    modifier = Modifier
                        .padding(horizontal = 1.dp)
                        .weight(1f)
                        .height(barHeight)
                        .background(barColor, RoundedCornerShape(4.dp))
                )
            }
        }
    }
}