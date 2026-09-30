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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import com.vescdash.ui.theme.HeavyWideFont
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Stark-style arc gauge: battery band, torque bar and big speed under three panels. */
@Composable
internal fun ClassicRide(d: RideData, c: RidePalette, onCycleMode: () -> Unit, onHome: () -> Unit) {
    val intro = rememberIntroProgress()
    val batteryFrac by animateFloatAsState(
        ((d.battery ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f), tween(500), label = "battery",
    )
    val torqueFrac by animateFloatAsState(d.torque, tween(120), label = "torque")
    val batColor = batteryColor(d.battery, c)
    val measurer = rememberTextMeasurer()
    val icons = rememberRideIcons()
    val pulse = rememberWarningPulse()
    val pillBorder = pillBorderColor(d.modeStatus, c)
    val boltColor = d.modeColor ?: c.textSoft

    // Key-on sweep: the battery band and torque bar run to full, then settle on live values.
    val bandFrac = introSweep(intro, if (d.battery == null) 0f else batteryFrac)
    val bandColor = if (d.battery == null) c.green else batColor
    val torque = introSweep(intro, torqueFrac)
    val scaleReveal = min(1f, intro * 2f)

    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg)) {
        val g = RideGeometry(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), c)

        Canvas(Modifier.fillMaxSize()) {
            drawDome(g)
            drawBatteryGlow(g, batColor, visible = d.battery != null)
            drawPanels(g, measurer)
            drawPowerMode(g, measurer, icons.bolt, d.modeNumber.toString(), boltColor, pillBorder)
            drawBatteryValue(g, measurer, d.battery, batColor)
            drawRidingTime(g, measurer, d.rideTimeMs)
            drawBatteryBand(g, bandFrac, bandColor, live = d.battery != null || intro < 1f)
            drawScale(g, measurer, scaleReveal)
            drawTorque(g, measurer, icons.bolt, torque)
            if (!d.stopped) drawSpeed(g, measurer, d.speed ?: 0.0, d.speedUnit)
            drawCentered(
                measurer, AnnotatedString("VESC DASH"),
                wide(0.026f * g.k, c.watermark, letterSpacing = 0.25f),
                Offset(g.cx, g.watermarkY),
            )
            // x clears a landscape camera cutout
            drawWarningStack(measurer, icons, c, d.warnings, x = 0.1f * g.w, y = 0.52f * g.h, size = 0.1f * g.h, pulse = pulse)
        }

        TapTarget(g.pill, enabled = true, onClick = onCycleMode)
        HomeButton(d.stopped, Offset(g.cx, g.homeY), g.homeD, c, onHome)
    }
}

/**
 * All positions are derived from the screen size so the layout scales across phones.
 * Angles follow Compose's convention: 0° = 3 o'clock, increasing clockwise.
 */
private class RideGeometry(val w: Float, val h: Float, val c: RidePalette) {
    // 0.93·h leaves a clear gap between the top of the dial's numbers and the panels above.
    val k = min(0.93f * h, w / 1.9f)
    val cx = w / 2f
    val cy = h + 0.41f * k

    // Top panels
    val panelBottom = 0.37f * h
    val panelMargin = 0.016f * w
    val panelWidth = (w - 2 * panelMargin) / 3f
    fun panelLeft(i: Int) = panelMargin + i * panelWidth
    fun panelCenterX(i: Int) = panelLeft(i) + panelWidth / 2f
    val labelY = 0.085f * h
    val valueY = 0.225f * h
    val pillH = 0.145f * h
    val pillW = maxOf(0.47f * panelWidth, pillH * 2.1f)
    val pill = Rect(
        panelCenterX(0) - pillW / 2f, valueY - pillH / 2f,
        panelCenterX(0) + pillW / 2f, valueY + pillH / 2f,
    )

    // Battery band + scale
    val bandStart = 220f
    val bandSweep = 100f
    val bandR = 0.90f * k
    val bandW = 0.063f * k
    val tickR = bandR + bandW / 2f + 0.012f * k
    val labelR = 1.03f * k

    // Torque bar
    val torqueR = 0.83f * k
    val regAngle = 242f
    val zeroAngle = 249f
    val maxAngle = 298f

    // Centre
    val speedY = cy - 0.66f * k
    val unitY = cy - 0.555f * k
    val watermarkY = cy - 0.47f * k
    val homeD = 0.15f * k
    val homeY = cy - 0.63f * k

    fun point(angleDeg: Float, r: Float): Offset {
        val a = angleDeg * PI.toFloat() / 180f
        return Offset(cx + r * cos(a), cy + r * sin(a))
    }
}

// ---- drawing ---------------------------------------------------------------

private fun DrawScope.arc(g: RideGeometry, color: Color, start: Float, sweep: Float, r: Float, width: Float, cap: StrokeCap = StrokeCap.Butt) {
    drawArc(color, start, sweep, false, Offset(g.cx - r, g.cy - r), Size(2 * r, 2 * r), style = Stroke(width, cap = cap))
}

private fun DrawScope.drawDome(g: RideGeometry) {
    val domeR = 1.1f * g.k
    drawCircle(
        Brush.radialGradient(listOf(g.c.domeCenter, g.c.domeEdge), Offset(g.cx, g.cy), domeR),
        domeR,
        Offset(g.cx, g.cy),
    )
    drawCircle(g.c.inner, 0.745f * g.k, Offset(g.cx, g.cy))
}

private fun DrawScope.drawBatteryGlow(g: RideGeometry, color: Color, visible: Boolean) {
    if (!visible) return
    val c = Offset(g.cx, 0.3f * g.h)
    val r = 0.34f * g.h
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.2f), Color.Transparent), c, r), r, c)
}

private fun DrawScope.drawPanels(g: RideGeometry, m: TextMeasurer) {
    val labelStyle = wide(0.024f * g.h, g.c.label, 0.08f)
    for (i in 0..2) {
        val left = g.panelLeft(i)
        drawRect(g.c.panel.copy(alpha = 0.92f), Offset(left, 0f), Size(g.panelWidth, g.panelBottom))
        drawLine(
            g.c.panelLine,
            Offset(left + 0.06f * g.panelWidth, 0.035f * g.h),
            Offset(left + 0.94f * g.panelWidth, 0.035f * g.h),
            strokeWidth = 1f,
        )
        if (i > 0) drawLine(g.c.panelLine, Offset(left, 0f), Offset(left, g.panelBottom), strokeWidth = 1f)
    }
    drawCentered(m, AnnotatedString("POWER MODE"), labelStyle, Offset(g.panelLeft(0) + 0.14f * g.panelWidth, g.labelY), alignX = 0f)
    drawCentered(m, AnnotatedString("BATTERY LEVEL"), labelStyle, Offset(g.panelCenterX(1), g.labelY))
    drawCentered(m, AnnotatedString("RIDING TIME"), labelStyle, Offset(g.panelLeft(2) + 0.86f * g.panelWidth, g.labelY), alignX = 1f)
}

private fun DrawScope.drawPowerMode(
    g: RideGeometry,
    m: TextMeasurer,
    bolt: VectorPainter,
    number: String,
    boltColor: Color,
    border: Color,
) {
    val p = g.pill
    val radius = CornerRadius(p.height / 2f)
    drawRoundRect(g.c.pill, p.topLeft, p.size, radius)
    drawRoundRect(border, p.topLeft, p.size, radius, style = Stroke(0.006f * g.h))
    val iconSize = 0.085f * g.h
    drawIcon(bolt, Offset(p.left + p.width * 0.34f, p.center.y), iconSize, boltColor)
    drawCentered(m, AnnotatedString(number), digits(g.c, 0.1f * g.h, g.c.text), Offset(p.left + p.width * 0.62f, p.center.y))
}

private fun DrawScope.drawBatteryValue(g: RideGeometry, m: TextMeasurer, battery: Double?, color: Color) {
    val text = buildAnnotatedString {
        append(battery?.roundToInt()?.toString() ?: "--")
        appendUnit(" %", (0.06f * g.h).toSp(), family = if (g.c.stark) HeavyWideFont else FontFamily.Default)
    }
    drawCentered(m, text, digits(g.c, 0.165f * g.h, color), Offset(g.panelCenterX(1), g.valueY))
}

private fun DrawScope.drawRidingTime(g: RideGeometry, m: TextMeasurer, ms: Long) {
    val text = ridingTimeText(
        ms,
        big = SpanStyle(fontSize = (0.105f * g.h).toSp(), color = g.c.text),
        small = SpanStyle(fontSize = (0.05f * g.h).toSp(), color = g.c.textSoft, fontFamily = if (g.c.stark) HeavyWideFont else null),
    )
    drawCentered(m, text, digits(g.c, 0.105f * g.h, g.c.text), Offset(g.panelCenterX(2), g.valueY))
}

private fun DrawScope.drawBatteryBand(g: RideGeometry, frac: Float, color: Color, live: Boolean) {
    // Track: darker at 0, lighter toward 100
    val trackBrush = Brush.sweepGradient(
        0f to g.c.track,
        (g.bandStart / 360f) to g.c.track,
        ((g.bandStart + g.bandSweep) / 360f) to g.c.trackEnd,
        1f to g.c.trackEnd,
        center = Offset(g.cx, g.cy),
    )
    drawArc(
        trackBrush, g.bandStart, g.bandSweep, false,
        Offset(g.cx - g.bandR, g.cy - g.bandR), Size(2 * g.bandR, 2 * g.bandR),
        style = Stroke(g.bandW),
    )
    if (live && frac > 0f) {
        if (g.c.stark) {
            // A halo that bleeds off both edges of the band
            arc(g, color.copy(alpha = 0.10f), g.bandStart, g.bandSweep * frac, g.bandR, g.bandW * 2.6f)
            arc(g, color.copy(alpha = 0.18f), g.bandStart, g.bandSweep * frac, g.bandR, g.bandW * 1.6f)
        } else {
            arc(g, color.copy(alpha = 0.07f), g.bandStart, g.bandSweep * frac, g.bandR, g.bandW * 1.7f)
        }
        arc(g, color, g.bandStart, g.bandSweep * frac, g.bandR, g.bandW)
        // and a brighter rim along the outer edge
        if (g.c.stark) drawArc(Color.White.copy(alpha = 0.22f), g.bandStart, g.bandSweep * frac, false, Offset(g.cx - g.bandR - g.bandW * 0.4f, g.cy - g.bandR - g.bandW * 0.4f), Size(2 * (g.bandR + g.bandW * 0.4f), 2 * (g.bandR + g.bandW * 0.4f)), style = Stroke(g.bandW * 0.2f))
    }
    // Fine segmentation across the band
    val inner = g.bandR - g.bandW / 2f
    val outer = g.bandR + g.bandW / 2f
    for (i in 0..100) {
        val a = g.bandStart + g.bandSweep * i / 100f
        drawLine(g.c.panel.copy(alpha = 0.45f), g.point(a, inner), g.point(a, outer), strokeWidth = 0.0035f * g.k)
    }
    // Small battery glyph at the start of the band
    val c = g.point(g.bandStart - 3.5f, g.bandR)
    val bw = 0.045f * g.k
    val bh = 0.026f * g.k
    val bodyColor = if (live) color else g.c.label
    drawRoundRect(bodyColor, Offset(c.x - bw / 2f, c.y - bh / 2f), Size(bw, bh), CornerRadius(bh * 0.2f), style = Stroke(0.006f * g.k))
    drawRect(bodyColor, Offset(c.x + bw / 2f, c.y - bh * 0.22f), Size(bh * 0.2f, bh * 0.44f))
    if (live) drawRect(bodyColor, Offset(c.x - bw / 2f + bh * 0.2f, c.y - bh * 0.3f), Size((bw - bh * 0.4f) * frac.coerceAtLeast(0.15f), bh * 0.6f))
}

private fun DrawScope.drawScale(g: RideGeometry, m: TextMeasurer, reveal: Float) {
    for (i in 0..(100 * reveal).toInt()) {
        val a = g.bandStart + g.bandSweep * i / 100f
        val major = i % 25 == 0
        val mid = i % 5 == 0
        val len = when {
            major -> 0.05f * g.k
            mid -> 0.034f * g.k
            else -> 0.018f * g.k
        }
        drawLine(
            if (major) g.c.tickMajor else g.c.tick,
            g.point(a, g.tickR),
            g.point(a, g.tickR + len),
            strokeWidth = if (major) 0.005f * g.k else 0.0028f * g.k,
        )
        if (g.c.stark && i == 100) {
            drawCentered(m, AnnotatedString("%"), wide(0.022f * g.k, g.c.label).copy(fontFamily = HeavyWideFont), g.point(a + 3.8f, g.labelR - 0.012f * g.k))
        }
        if (mid) {
            val style = if (major) {
                wide(0.036f * g.k, g.c.tickMajor)
            } else {
                wide(0.024f * g.k, g.c.label)
            }
            drawCentered(m, AnnotatedString(i.toString()), style, g.point(a, g.labelR))
        }
    }
}

private fun DrawScope.drawTorque(g: RideGeometry, m: TextMeasurer, bolt: VectorPainter, frac: Float) {
    arc(g, g.c.torqueTrack, g.regAngle, g.maxAngle - g.regAngle, g.torqueR, 0.012f * g.k)
    // Fine ticks just inside the torque track
    var a = g.regAngle
    while (a <= g.maxAngle + 0.01f) {
        drawLine(g.c.tick, g.point(a, g.torqueR - 0.045f * g.k), g.point(a, g.torqueR - 0.025f * g.k), strokeWidth = 0.0022f * g.k)
        a += 1.4f
    }
    if (frac > 0.005f) {
        val sweep = (g.maxAngle - g.zeroAngle) * frac
        arc(g, g.c.cyan.copy(alpha = 0.18f), g.zeroAngle, sweep, g.torqueR, 0.045f * g.k, StrokeCap.Round)
        arc(g, g.c.cyan, g.zeroAngle, sweep, g.torqueR, 0.014f * g.k, StrokeCap.Round)
    } else if (frac < -0.005f) {
        val sweep = (g.regAngle - g.zeroAngle) * -frac
        arc(g, g.c.green.copy(alpha = 0.18f), g.zeroAngle, sweep, g.torqueR, 0.045f * g.k, StrokeCap.Round)
        arc(g, g.c.green, g.zeroAngle, sweep, g.torqueR, 0.014f * g.k, StrokeCap.Round)
    }
    val labelStyle = wide(0.019f * g.k, g.c.label)
    val r = g.torqueR - 0.075f * g.k
    if (g.c.stark) {
        drawCentered(m, AnnotatedString("Nm"), labelStyle, g.point(g.maxAngle + 5f, g.torqueR), rotation = g.maxAngle + 5f + 90f)
    }
    drawCentered(m, AnnotatedString("REG"), labelStyle, g.point(g.regAngle + 1.5f, r), rotation = g.regAngle + 1.5f + 90f)
    drawCentered(m, AnnotatedString("0"), labelStyle, g.point(g.zeroAngle, r), rotation = g.zeroAngle + 90f)
    drawCentered(m, AnnotatedString("MAX"), labelStyle, g.point(g.maxAngle - 2f, r), rotation = g.maxAngle - 2f + 90f)
    drawIcon(bolt, g.point(g.regAngle - 4.5f, g.torqueR - 0.02f * g.k), 0.04f * g.k, g.c.label)
}

private fun DrawScope.drawSpeed(g: RideGeometry, m: TextMeasurer, speed: Double, unit: String) {
    drawCentered(m, AnnotatedString(speed.roundToInt().toString()), digits(g.c, 0.135f * g.k, g.c.speed), Offset(g.cx, g.speedY))
    drawCentered(m, AnnotatedString(unit), wide(0.036f * g.k, g.c.unit, 0.05f), Offset(g.cx, g.unitY))
}
