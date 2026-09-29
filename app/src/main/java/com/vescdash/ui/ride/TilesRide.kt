package com.vescdash.ui.ride

import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.delay
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pared-back tile layout: one rounded strip of three tiles (mode, power ring, battery),
 * speed and trip in the middle, and a quiet status line (clock, riding time, motor temp).
 */
@Composable
internal fun TilesRide(d: RideData, c: RidePalette, onCycleMode: () -> Unit, onHome: () -> Unit) {
    val intro = rememberIntroProgress()
    val measurer = rememberTextMeasurer()
    val icons = rememberRideIcons()
    val pulse = rememberWarningPulse()
    val torqueFrac by animateFloatAsState(d.torque, tween(150), label = "ring")
    val ring = introSweep(intro, torqueFrac)
    val stripIn = introPhase(intro, 0f, 0.45f)
    val centerIn = introPhase(intro, 0.15f, 0.65f)

    val context = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    val clock = DateFormat.getTimeFormat(context).format(Date(now))

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.tilesBgTop, c.tilesBgBottom))),
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val margin = 0.035f * w
        val strip = Rect(margin, 0.05f * h, w - margin, 0.05f * h + 0.34f * h)
        val tileW = strip.width / 3f
        val modePill = pillRect(strip.left + tileW / 2f, strip.center.y, 0.2f * w, 0.15f * h)

        // Tile strip
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = stripIn
                    translationY = -(1f - stripIn) * 0.1f * h
                },
        ) {
            drawRoundRect(c.tile, strip.topLeft, strip.size, CornerRadius(0.07f * h))
            for (i in 1..2) {
                val x = strip.left + tileW * i
                drawLine(c.tilesBgTop, Offset(x, strip.top), Offset(x, strip.bottom), strokeWidth = 0.004f * w)
            }

            // Mode: dark pill with bolt and number
            drawRoundRect(c.tilePill, modePill.topLeft, modePill.size, CornerRadius(modePill.height / 2f))
            drawRoundRect(pillBorderColor(d.modeStatus, c), modePill.topLeft, modePill.size, CornerRadius(modePill.height / 2f), style = Stroke(0.004f * h))
            drawIcon(icons.bolt, Offset(modePill.left + modePill.width * 0.32f, modePill.center.y), 0.1f * h, d.modeColor ?: c.green)
            drawCentered(measurer, AnnotatedString(d.modeNumber.toString()), wide(0.105f * h, c.text), Offset(modePill.left + modePill.width * 0.62f, modePill.center.y))

            // Power ring: fills clockwise as you pull, anticlockwise (green) on regen
            val ringCenter = Offset(strip.center.x, strip.top + strip.height * 0.42f)
            val r = 0.085f * h
            val stroke = 0.022f * h
            drawCircle(c.textSoft.copy(alpha = 0.28f), r, ringCenter, style = Stroke(stroke))
            if (abs(ring) > 0.003f) {
                drawArc(
                    if (ring > 0f) c.text else c.green,
                    -90f, 360f * ring, false,
                    Offset(ringCenter.x - r, ringCenter.y - r), Size(2 * r, 2 * r),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            val kw = d.powerKw?.let { String.format(Locale.US, if (abs(it) < 10) "%.1f kW" else "%.0f kW", it) } ?: "-- kW"
            drawCentered(measurer, AnnotatedString(kw), wide(0.038f * h, c.label, 0.05f), Offset(ringCenter.x, strip.top + strip.height * 0.85f))

            // Battery: pill in the battery colour with a small cell glyph
            drawBatteryPill(measurer, c, d.battery, Offset(strip.right - tileW / 2f, strip.center.y), 0.21f * w, 0.15f * h)
        }

        // Speed and trip
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = centerIn
                    val s = 0.92f + 0.08f * centerIn
                    scaleX = s
                    scaleY = s
                },
        ) {
            val speed = buildAnnotatedString {
                append(d.speed?.roundToInt()?.toString() ?: "--")
                withStyle(SpanStyle(fontSize = (0.05f * h).toSp(), color = c.textSoft)) { append(" ${d.speedUnit}") }
            }
            drawCentered(measurer, speed, wide(0.17f * h, if (d.speed == null) c.label else c.text), Offset(w / 2f, 0.585f * h))
            val trip = d.trip?.let { String.format(Locale.US, "%.1f %s", it, d.tripUnit) } ?: "-- ${d.tripUnit}"
            drawCentered(measurer, AnnotatedString("TRIP  $trip"), wide(0.026f * h, c.label, 0.08f), Offset(w / 2f, 0.7f * h))
        }

        // Status line and warnings
        Canvas(Modifier.fillMaxSize()) {
            val y = 0.93f * h
            val style = wide(0.032f * h, c.label, 0.06f)
            val total = d.rideTimeMs / 60_000
            drawCentered(measurer, AnnotatedString(clock), style, Offset(margin + 0.01f * w, y), alignX = 0f)
            drawCentered(measurer, AnnotatedString("RIDE  ${total / 60}h ${(total % 60).toString().padStart(2, '0')}m"), style, Offset(w / 2f, y))
            // Michroma draws ° as a small "o", so the unit uses the system face.
            val motor = buildAnnotatedString {
                append("MOTOR  ")
                append(d.motorTempC?.roundToInt()?.toString() ?: "--")
                withStyle(SpanStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium)) { append(" °C") }
            }
            drawCentered(measurer, motor, style, Offset(w - margin - 0.01f * w, y), alignX = 1f)

            // Warnings sit in a compact row in the bottom-left, just above the clock.
            drawWarningStack(
                measurer, icons, c, d.warnings,
                x = margin + 0.045f * h, y = 0.82f * h, size = 0.08f * h, pulse = pulse, horizontal = true,
            )
        }

        TapTarget(modePill, enabled = true, onClick = onCycleMode)
        HomeButton(d.stopped, Offset(0.915f * w, 0.585f * h), 0.15f * h, c, onHome)
    }
}

private fun pillRect(cx: Float, cy: Float, w: Float, h: Float) = Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)

private fun DrawScope.drawBatteryPill(m: TextMeasurer, c: RidePalette, battery: Double?, center: Offset, pw: Float, ph: Float) {
    val pill = pillRect(center.x, center.y, pw, ph)
    val fill = if (battery == null) c.tilePill else batteryColor(battery, c)
    val ink = if (battery == null) c.label else c.onAccent
    drawRoundRect(fill, pill.topLeft, pill.size, CornerRadius(ph / 2f))
    // cell glyph
    val gw = ph * 0.42f
    val gh = ph * 0.26f
    val gx = pill.left + pw * 0.2f - gw / 2f
    val gy = center.y - gh / 2f
    drawRoundRect(ink, Offset(gx, gy), Size(gw, gh), CornerRadius(gh * 0.2f), style = Stroke(ph * 0.05f))
    drawRect(ink, Offset(gx + gw, center.y - gh * 0.2f), Size(gh * 0.18f, gh * 0.4f))
    drawRect(ink, Offset(gx + gh * 0.18f, gy + gh * 0.22f), Size((gw - gh * 0.36f) * ((battery ?: 0.0) / 100.0).toFloat().coerceIn(0.1f, 1f), gh * 0.56f))
    drawCentered(
        m, AnnotatedString(battery?.roundToInt()?.toString() ?: "--"),
        wide(ph * 0.62f, ink),
        Offset(pill.left + pw * 0.6f, center.y),
    )
}
