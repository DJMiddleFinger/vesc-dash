package com.vescdash.ui.ride

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import com.vescdash.data.VehicleMath
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Length of the Alpha-mode launch sequence. */
internal const val ALPHA_CINEMATIC_MS = 2600
private const val DURATION_S = ALPHA_CINEMATIC_MS / 1000f
private const val SLAM_S = 0.18f

internal class CinematicInfo(val modeNumber: Int, val color: Color, val peakKw: Double)

private class Streak(val angle: Float, val speed: Float, val offset: Float, val width: Float, val white: Boolean)
private class Spark(val angle: Float, val speed: Float, val length: Float)

// ---- timeline helpers ----------------------------------------------------------------

/** Eased 0 → 1 while [t] moves from [start] to [end] seconds. */
private fun phase(t: Float, start: Float, end: Float) =
    FastOutSlowInEasing.transform(((t - start) / (end - start)).coerceIn(0f, 1f))

/** Sharp hit at [at] that decays to nothing over [length] seconds. */
private fun impact(t: Float, at: Float, length: Float): Float {
    val dt = t - at
    if (dt < 0f || dt > length) return 0f
    val k = 1f - dt / length
    return k * k
}

/** Camera shake applied to the ride screen under the overlay. */
internal fun cinematicShake(progress: Float, amplitudePx: Float): Offset {
    val t = progress * DURATION_S
    val env = max(impact(t, 0f, 0.3f) * 0.6f, impact(t, SLAM_S, 0.75f))
    if (env == 0f) return Offset.Zero
    return Offset(
        (sin(t * 71f) + 0.5f * sin(t * 133f)) * amplitudePx * env,
        (cos(t * 59f) + 0.5f * sin(t * 97f)) * amplitudePx * env,
    )
}

/** Brief zoom punch on the ride screen when the word slams in. */
internal fun cinematicPunch(progress: Float): Float = 1f + 0.04f * impact(progress * DURATION_S, SLAM_S, 0.5f)

// ---- overlay ----------------------------------------------------------------------------

/**
 * Launch sequence for the Alpha (highest-power) mode: flash, letterbox, hyperspace streaks,
 * the word slamming in with ghost trails and a chromatic split, shockwaves, sparks, a typed
 * stat line and a power meter filling in the lower bar.
 */
@Composable
internal fun AlphaCinematic(progress: Float, info: CinematicInfo, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val bolt = rememberVectorPainter(Icons.Filled.Bolt)
    val streaks = remember {
        val r = Random(7)
        List(60) {
            Streak(
                angle = r.nextFloat() * 2f * PI.toFloat(),
                speed = 0.9f + r.nextFloat() * 1.6f,
                offset = r.nextFloat(),
                width = 1.5f + r.nextFloat() * 3f,
                white = r.nextFloat() < 0.45f,
            )
        }
    }
    val sparks = remember {
        val r = Random(11)
        List(40) { Spark(r.nextFloat() * 2f * PI.toFloat(), 0.5f + r.nextFloat() * 1.3f, 0.02f + r.nextFloat() * 0.05f) }
    }
    val stats = remember(info) {
        val kw = info.peakKw
        "MODE ${info.modeNumber}   ·   ${String.format(Locale.US, if (kw < 10) "%.1f" else "%.0f", kw)} kW   ·   ${VehicleMath.kwToHp(kw).roundToInt()} HP"
    }

    Canvas(modifier) {
        val t = progress * DURATION_S
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h * 0.47f)
        val color = info.color
        val fadeOut = 1f - phase(t, 2.05f, DURATION_S)

        // Dim the gauges and wash the edges in the mode colour.
        drawRect(Color.Black.copy(alpha = 0.9f * phase(t, 0f, 0.12f) * fadeOut))
        val vignette = (0.38f + 0.22f * sin(t * 14f)) * phase(t, 0f, 0.15f) * fadeOut
        drawRect(Brush.radialGradient(listOf(Color.Transparent, color.copy(alpha = vignette)), center, max(w, h) * 0.72f))

        // Giant faint bolt behind everything.
        val boltSize = h * (0.95f + 0.12f * phase(t, 0f, 2f))
        drawIcon(bolt, center, boltSize, color.copy(alpha = 0.13f * phase(t, 0.1f, 0.4f) * fadeOut))

        drawStreaks(streaks, t, center, w, h, color)
        drawShockwaves(t, center, w, h, color)
        drawSparks(sparks, t, center, w, h, color)
        drawSlamWord(measurer, t, center, h, color, fadeOut)

        // Stat line types itself out under the word.
        val typed = (stats.length * phase(t, 0.45f, 1.05f)).toInt()
        if (typed > 0) {
            drawCentered(
                measurer, AnnotatedString(stats.take(typed)),
                wide(h * 0.042f, color.copy(alpha = fadeOut), 0.12f),
                Offset(center.x, center.y + h * 0.22f),
            )
        }

        drawLetterbox(measurer, t, w, h, color, fadeOut)

        // Opening flash, then a shorter hit on the slam.
        val flash = (1f - t / 0.14f).coerceIn(0f, 1f)
        if (flash > 0f) drawRect(Color.White.copy(alpha = 0.75f * flash))
        val slamFlash = impact(t, SLAM_S, 0.12f)
        if (slamFlash > 0f) drawRect(color.copy(alpha = 0.4f * slamFlash))
    }
}

private fun DrawScope.drawStreaks(streaks: List<Streak>, t: Float, center: Offset, w: Float, h: Float, color: Color) {
    val env = phase(t, 0.05f, 0.25f) * (1f - phase(t, 1.1f, 1.8f))
    if (env <= 0f) return
    for (s in streaks) {
        val r = (t * s.speed + s.offset) % 1f
        val inner = h * 0.14f + r * w * 0.72f
        val len = w * 0.09f * (0.35f + r)
        val dir = Offset(cos(s.angle), sin(s.angle))
        drawLine(
            (if (s.white) Color.White else color).copy(alpha = (1f - r) * 0.8f * env),
            center + dir * inner,
            center + dir * (inner + len),
            strokeWidth = s.width,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawShockwaves(t: Float, center: Offset, w: Float, h: Float, color: Color) {
    for ((start, strength) in listOf(SLAM_S to 1f, SLAM_S + 0.14f to 0.6f)) {
        val dt = t - start
        if (dt < 0f || dt > 0.8f) continue
        val k = dt / 0.8f
        val radius = FastOutSlowInEasing.transform(k) * w * 0.8f
        drawCircle(
            color.copy(alpha = (1f - k) * 0.85f * strength),
            radius,
            center,
            style = Stroke(h * 0.035f * (1f - k) * strength + 2f),
        )
    }
}

private fun DrawScope.drawSparks(sparks: List<Spark>, t: Float, center: Offset, w: Float, h: Float, color: Color) {
    val dt = t - SLAM_S
    if (dt < 0f || dt > 1.1f) return
    val alpha = 1f - dt / 1.1f
    for (s in sparks) {
        val dir = Offset(cos(s.angle), sin(s.angle))
        val travel = s.speed * w * 0.45f * (1f - exp(-dt * 3.2f))
        val gravity = Offset(0f, h * 0.35f * dt * dt)
        val head = center + dir * travel + gravity
        val tail = head - dir * (w * s.length)
        drawLine(Color.White.copy(alpha = alpha), tail, head, strokeWidth = 3f, cap = StrokeCap.Round)
        drawCircle(color.copy(alpha = alpha * 0.8f), 3.5f, head)
    }
}

private fun DrawScope.drawSlamWord(measurer: TextMeasurer, t: Float, center: Offset, h: Float, color: Color, fadeOut: Float) {
    val word = AnnotatedString("ALPHA")
    val base = h * 0.3f
    val (s, alpha) = if (t < SLAM_S) {
        val k = t / SLAM_S
        (3.4f - 2.4f * k * k) to k
    } else {
        val dt = t - SLAM_S
        (1f + 0.1f * exp(-dt * 9f) * cos(dt * 28f)) to 1f
    }
    val a = alpha * fadeOut
    if (a <= 0f) return

    // Ghost trails left by the impact.
    if (t >= SLAM_S) {
        val g = exp(-(t - SLAM_S) * 5f)
        for (i in 3 downTo 1) {
            scale(s * (1f + 0.14f * i * g), pivot = center) {
                drawCentered(measurer, word, wide(base, color.copy(alpha = 0.3f * g / i * a), 0.06f), center)
            }
        }
    }
    // Glow
    scale(s * 1.04f, pivot = center) {
        drawCentered(measurer, word, wide(base, color.copy(alpha = 0.45f * a), 0.06f), center)
    }
    // Chromatic split right after the slam
    val split = impact(t, SLAM_S, 0.5f) * h * 0.02f
    if (split > 0.5f) {
        scale(s, pivot = center) {
            drawCentered(measurer, word, wide(base, Color(0xFFFF2040).copy(alpha = 0.7f * a), 0.06f), center + Offset(-split, 0f))
            drawCentered(measurer, word, wide(base, Color(0xFF20E0FF).copy(alpha = 0.7f * a), 0.06f), center + Offset(split, 0f))
        }
    }
    scale(s, pivot = center) {
        drawCentered(measurer, word, wide(base, Color.White.copy(alpha = a), 0.06f), center)
    }
}

private fun DrawScope.drawLetterbox(measurer: TextMeasurer, t: Float, w: Float, h: Float, color: Color, fadeOut: Float) {
    val barH = h * 0.13f * phase(t, 0f, 0.28f) * (1f - phase(t, 2.05f, 2.45f))
    if (barH <= 0.5f) return
    drawRect(Color.Black, Offset.Zero, Size(w, barH))
    drawRect(Color.Black, Offset(0f, h - barH), Size(w, barH))
    val edge = color.copy(alpha = 0.9f * fadeOut)
    drawLine(edge, Offset(0f, barH), Offset(w, barH), strokeWidth = 3f)
    drawLine(edge, Offset(0f, h - barH), Offset(w, h - barH), strokeWidth = 3f)

    // Top bar: title
    val labelAlpha = phase(t, 0.3f, 0.6f) * fadeOut
    if (labelAlpha > 0f) {
        drawCentered(
            measurer, AnnotatedString("ALPHA MODE"),
            wide(barH * 0.34f, Color.White.copy(alpha = labelAlpha), 0.45f),
            Offset(w / 2f, barH / 2f),
        )
    }

    // Bottom bar: power meter slamming to 100 %
    val fill = phase(t, 0.4f, 1.0f)
    val meterY = h - barH / 2f
    val x0 = w * 0.3f
    val x1 = w * 0.7f
    val th = barH * 0.14f
    drawLine(Color.White.copy(alpha = 0.18f * fadeOut), Offset(x0, meterY), Offset(x1, meterY), strokeWidth = th, cap = StrokeCap.Round)
    if (fill > 0f) {
        drawLine(color.copy(alpha = 0.35f * fadeOut), Offset(x0, meterY), Offset(x0 + (x1 - x0) * fill, meterY), strokeWidth = th * 3f, cap = StrokeCap.Round)
        drawLine(color.copy(alpha = fadeOut), Offset(x0, meterY), Offset(x0 + (x1 - x0) * fill, meterY), strokeWidth = th, cap = StrokeCap.Round)
    }
    val labels = wide(barH * 0.22f, Color.White.copy(alpha = labelAlpha), 0.2f)
    drawCentered(measurer, AnnotatedString("POWER"), labels, Offset(x0 - w * 0.02f, meterY), alignX = 1f)
    drawCentered(measurer, AnnotatedString("${(fill * 100).roundToInt()}%"), labels, Offset(x1 + w * 0.02f, meterY), alignX = 0f)
}

// ---- haptics ------------------------------------------------------------------------------

/** Kick on the flash, a heavy hit on the slam, then a fading rumble. */
internal fun playAlphaHaptics(context: Context) {
    val vibrator = vibrator(context) ?: return
    if (!vibrator.hasVibrator()) return
    val timings = longArrayOf(0, 45, 135, 140, 60, 45, 55, 45, 55, 90)
    val amplitudes = intArrayOf(0, 255, 0, 255, 0, 170, 0, 120, 0, 70)
    runCatching { vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1)) }
}

/** One short, light tick as a launch kicks in. */
internal fun playLaunchHaptic(context: Context) {
    val vibrator = vibrator(context) ?: return
    if (!vibrator.hasVibrator()) return
    runCatching { vibrator.vibrate(VibrationEffect.createOneShot(28, 90)) }
}

private fun vibrator(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        legacyVibrator(context)
    }

@Suppress("DEPRECATION")
private fun legacyVibrator(context: Context): Vibrator? = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
