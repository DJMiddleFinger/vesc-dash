package com.vescdash.ui.ride

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val STREAK_COUNT = 56

/** Fixed pseudo-random streaks, kept as arrays so drawing them never allocates. */
private class StreakField {
    private val r = Random(23)
    val dirX = FloatArray(STREAK_COUNT)
    val dirY = FloatArray(STREAK_COUNT)
    val speed = FloatArray(STREAK_COUNT) { 0.8f + r.nextFloat() * 1.7f }
    val offset = FloatArray(STREAK_COUNT) { r.nextFloat() }
    /** Stroke width as a fraction of the screen height. */
    val width = FloatArray(STREAK_COUNT) { 0.003f + r.nextFloat() * 0.007f }
    val tinted = BooleanArray(STREAK_COUNT) { r.nextFloat() < 0.6f }

    init {
        for (i in 0 until STREAK_COUNT) {
            val angle = r.nextFloat() * 2f * Math.PI.toFloat()
            dirX[i] = cos(angle)
            dirY[i] = sin(angle)
        }
    }
}

/**
 * Launch-control look for hard acceleration: streaks stretching out from the centre and a glow
 * washing in from the screen edges. Drawn over the ride view without taking touches.
 * [intensity] (0..1) and [phase] (the streaks' travel, in cycles) are read while drawing, so
 * they can change every frame without recomposing anything. [glow] tints the edges and part of
 * the streaks; the rest use [streak], which should contrast with the ride background.
 */
@Composable
internal fun LaunchEffect(intensity: () -> Float, phase: () -> Float, glow: Color, streak: Color, modifier: Modifier = Modifier) {
    val field = remember { StreakField() }
    Box(
        modifier
            .drawWithCache {
                val w = size.width
                val h = size.height
                val depth = h * 0.2f
                val bottom = h * 0.26f
                // Strong at the very edge, falling off quickly so the widgets stay clear.
                val stops = arrayOf(0f to glow.copy(alpha = 0.7f), 0.3f to glow.copy(alpha = 0.2f), 1f to Color.Transparent)
                val top = Brush.verticalGradient(*stops, startY = 0f, endY = depth)
                val base = Brush.verticalGradient(*stops, startY = h, endY = h - bottom)
                val left = Brush.horizontalGradient(*stops, startX = 0f, endX = depth)
                val right = Brush.horizontalGradient(*stops, startX = w, endX = w - depth)
                val center = Offset(w / 2f, h / 2f)
                val inner = h * 0.32f
                val reach = hypot(w, h) * 0.55f
                onDrawBehind {
                    val i = intensity()
                    if (i <= 0.01f) return@onDrawBehind
                    val p = phase()
                    // Edge glow, pulsing a little with the surge.
                    val a = i * (0.85f + 0.15f * sin(p * 28f))
                    drawRect(top, Offset.Zero, Size(w, depth), alpha = a * 0.7f)
                    drawRect(base, Offset(0f, h - bottom), Size(w, bottom), alpha = a)
                    drawRect(left, Offset.Zero, Size(depth, h), alpha = a * 0.7f)
                    drawRect(right, Offset(w - depth, 0f), Size(depth, h), alpha = a * 0.7f)

                    // Streaks fly out of a ring around the centre, speeding up and lengthening as they go.
                    val lengthScale = 0.4f + 0.6f * i
                    for (k in 0 until STREAK_COUNT) {
                        val r = (p * field.speed[k] + field.offset[k]) % 1f
                        val dist = inner + r * r * reach
                        val len = h * (0.04f + 0.26f * r) * lengthScale
                        val fade = min(r * 8f, 1f) * (1f - r)
                        val dir = Offset(field.dirX[k], field.dirY[k])
                        drawLine(
                            (if (field.tinted[k]) glow else streak).copy(alpha = 0.6f * i * fade),
                            center + dir * dist,
                            center + dir * (dist + len),
                            strokeWidth = field.width[k] * h * (0.5f + r),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            },
    )
}
