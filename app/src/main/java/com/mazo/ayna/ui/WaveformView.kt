package com.mazo.ayna.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Animated audio waveform — purple/pink vertical bars with a centre line,
 * like the reference main screen.
 *
 * Animates only while [listening] is true; otherwise shows a calm,
 * low-amplitude static waveform.
 */
@Composable
fun WaveformView(
    listening: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveformPhase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val barCount = 48
        val gap = size.width / barCount
        val barWidth = gap * 0.45f
        val centreY = size.height / 2f

        val gradient = Brush.verticalGradient(
            colors = listOf(
                AynaColors.Primary,
                AynaColors.AccentPink,
                AynaColors.Secondary
            )
        )

        // Faint horizontal centre line, as in the reference image.
        drawLine(
            color = Color.White.copy(alpha = 0.25f),
            start = Offset(0f, centreY),
            end = Offset(size.width, centreY),
            strokeWidth = 1.5f
        )

        for (i in 0 until barCount) {
            // Symmetric envelope: tall in the middle, short at the edges.
            val position = i.toFloat() / (barCount - 1)
            val envelope = 1f - abs(position - 0.5f) * 2f // 0 at edges, 1 at centre
            val envelopeShaped = 0.15f + 0.85f * envelope * envelope

            val wave = if (listening) {
                0.35f + 0.65f * abs(sin(phase + i * 0.55f))
            } else {
                0.35f + 0.15f * abs(sin(i * 0.9f))
            }

            val barHeight = size.height * envelopeShaped * wave
            val x = i * gap + (gap - barWidth) / 2f
            drawRoundRect(
                brush = gradient,
                topLeft = Offset(x, centreY - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
