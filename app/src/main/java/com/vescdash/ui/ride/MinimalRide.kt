package com.vescdash.ui.ride

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.roundToInt

/**
 * Stripped-back ride screen: mode, battery and riding time along the top, one huge speed
 * readout, and a single power/regen bar along the bottom.
 */
@Composable
internal fun MinimalRide(d: RideData, c: RidePalette, onCycleMode: () -> Unit, onHome: () -> Unit) {
    val intro = rememberIntroProgress()
    val batteryFrac by animateFloatAsState(
        ((d.battery ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f), tween(500), label = "battery",
    )
    val torqueFrac by animateFloatAsState(d.torque, tween(120), label = "torque")
    val measurer = rememberTextMeasurer()
    val icons = rememberRideIcons()
    val pulse = rememberWarningPulse()
    val batColor = batteryColor(d.battery, c)

    // Entry: top row drops in, speed scales up, bars sweep to full and settle.
    val topIn = introPhase(intro, 0f, 0.45f)
    val speedIn = introPhase(intro, 0.15f, 0.65f)
    val cellFrac = introSweep(intro, if (d.battery == null) 0f else batteryFrac)
    val barFrac = introSweep(intro, torqueFrac)

    BoxWithConstraints(Modifier.fillMaxSize().background(c.minimalBg)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val cx = w / 2f
        val topY = 0.12f * h
        val pill = Rect(0.035f * w, topY - 0.065f * h, 0.035f * w + 0.26f * w, topY + 0.065f * h)

        // Top row
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = topIn
                    translationY = -(1f - topIn) * 0.12f * h
                },
        ) {
            drawModePill(measurer, icons, c, d, pill)

            // Battery: outlined cell with fill, then the percentage
            val pctText = buildAnnotatedString {
                append(d.battery?.roundToInt()?.toString() ?: "--")
                appendUnit(" %", (0.05f * h).toSp())
            }
            val pctStyle = wide(0.075f * h, if (d.battery == null) c.label else c.text)
            val pctWidth = measurer.measure(pctText, pctStyle).size.width
            val bw = 0.12f * w
            val bh = 0.07f * h
            val gap = 0.018f * w
            val left = cx - (bw + gap + pctWidth) / 2f
            val stroke = 0.006f * h
            drawRoundRect(c.textSoft, Offset(left, topY - bh / 2f), Size(bw, bh), CornerRadius(bh * 0.25f), style = Stroke(stroke))
            drawRoundRect(c.textSoft, Offset(left + bw + stroke, topY - bh * 0.2f), Size(bh * 0.12f, bh * 0.4f), CornerRadius(bh * 0.05f))
            val pad = bh * 0.18f
            if (cellFrac > 0f) {
                val fillColor = if (d.battery == null) c.green else batColor
                drawRoundRect(
                    fillColor,
                    Offset(left + pad, topY - bh / 2f + pad),
                    Size((bw - 2 * pad) * cellFrac, bh - 2 * pad),
                    CornerRadius(bh * 0.12f),
                )
            }
            drawCentered(measurer, pctText, pctStyle, Offset(left + bw + gap, topY), alignX = 0f)

            // Riding time
            val time = ridingTimeText(
                d.rideTimeMs,
                big = SpanStyle(fontSize = (0.075f * h).toSp(), color = c.text),
                small = SpanStyle(fontSize = (0.036f * h).toSp(), color = c.textSoft),
            )
            drawCentered(measurer, time, wide(0.075f * h, c.text), Offset(0.965f * w, topY), alignX = 1f)
        }

        // Speed
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = speedIn
                    val s = 0.88f + 0.12f * speedIn
                    scaleX = s
                    scaleY = s
                },
        ) {
            val speedY = 0.5f * h
            val value = d.speed?.roundToInt()?.toString() ?: "--"
            drawCentered(measurer, AnnotatedString(value), wide(0.46f * h, if (d.speed == null) c.label else c.text), Offset(cx, speedY))
            drawCentered(measurer, AnnotatedString(d.speedUnit), wide(0.05f * h, c.label, 0.08f), Offset(cx, speedY + 0.29f * h))
        }

        // Power / regen bar and warnings
        Canvas(Modifier.fillMaxSize()) {
            drawPowerBar(measurer, c, barFrac, y = 0.91f * h, x0 = 0.14f * w, x1 = 0.86f * w, thickness = 0.02f * h, labelSize = 0.03f * h)

            drawWarningStack(measurer, icons, c, d.warnings, x = 0.075f * w, y = 0.36f * h, size = 0.12f * h, pulse = pulse)
        }

        TapTarget(pill, enabled = true, onClick = onCycleMode)
        HomeButton(d.stopped, Offset(0.92f * w, 0.5f * h), 0.17f * h, c, onHome)
    }
}
