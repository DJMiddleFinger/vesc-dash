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
import kotlin.math.roundToInt

/**
 * Pared-back tile layout: one rounded strip of three tiles (mode, speed, battery), a
 * power/regen bar with the trip beneath it, and a quiet status line (clock, riding time,
 * motor temp).
 */
@Composable
internal fun TilesRide(d: RideData, c: RidePalette, onCycleMode: () -> Unit, onHome: () -> Unit) {
    val intro = rememberIntroProgress()
    val measurer = rememberTextMeasurer()
    val icons = rememberRideIcons()
    val pulse = rememberWarningPulse()
    val torqueFrac by animateFloatAsState(d.torque, tween(120), label = "torque")
    val barFrac = introSweep(intro, torqueFrac)
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

            // Speed in the centre tile, unit underneath
            drawCentered(
                measurer, AnnotatedString(d.speed?.roundToInt()?.toString() ?: "--"),
                wide(0.16f * h, if (d.speed == null) c.label else c.text),
                Offset(strip.center.x, strip.top + strip.height * 0.43f),
            )
            drawCentered(
                measurer, AnnotatedString(d.speedUnit),
                wide(0.036f * h, c.label, 0.08f),
                Offset(strip.center.x, strip.top + strip.height * 0.84f),
            )

            // Battery: pill in the battery colour with a small cell glyph
            drawBatteryPill(measurer, c, d.battery, Offset(strip.right - tileW / 2f, strip.center.y), 0.21f * w, 0.15f * h)
        }

        // Power / regen bar with the trip beneath it
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = centerIn },
        ) {
            drawPowerBar(measurer, c, barFrac, y = 0.57f * h, x0 = 0.14f * w, x1 = 0.86f * w, thickness = 0.026f * h, labelSize = 0.032f * h)
            val trip = d.trip?.let { String.format(Locale.US, "%.1f %s", it, d.tripUnit) } ?: "-- ${d.tripUnit}"
            drawCentered(measurer, AnnotatedString("TRIP  $trip"), wide(0.026f * h, c.label, 0.08f), Offset(w / 2f, 0.68f * h))
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
        HomeButton(d.stopped, Offset(w - margin - 0.07f * h, 0.8f * h), 0.13f * h, c, onHome)
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
