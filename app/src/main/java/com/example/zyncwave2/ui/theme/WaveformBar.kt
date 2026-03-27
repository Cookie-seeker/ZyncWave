package com.example.zyncwave2.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp

@Composable

fun WaveformBar(
    values: IntArray,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    onSeek: ((Float)->Unit)? = null
){
    val color = Color.Black
    val backgroundColor = Color.Black.copy(alpha = 0.2f)
    var bars= values.size

    Box (modifier = modifier
        .pointerInput (Unit){
            detectTapGestures { offset ->
                if (onSeek!= null){
                    val percent = offset.x/size.width
                    onSeek(percent.coerceIn(0f,1f))
                }
            }
        }
        .height(200.dp)
        .fillMaxWidth()
        .background(Color.Transparent)

    ){
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {values.forEachIndexed{index, v ->
            val barColor = if (index<(bars*progress).toInt()) color else backgroundColor

            Box (
                modifier = Modifier
                    .padding(horizontal = 1.dp)
                    .weight(1f)
                    .height((v.dp*1.1f).coerceAtLeast(4.dp))
                    .background(barColor, shape = RoundedCornerShape(4.dp))
                    .padding(horizontal = 1.dp)
            )
        } }
    }
}