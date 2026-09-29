package com.vescdash.ui.ride

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.em
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vescdash.data.Metric
import com.vescdash.data.ModeApplyStatus
import com.vescdash.data.defaultThresholds
import com.vescdash.data.value
import com.vescdash.ui.MainViewModel
import com.vescdash.ui.theme.WideFont
import com.vescdash.vesc.VescProtocol
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private object RideColors {
    val Bg = Color(0xFF111113)
    val Panel = Color(0xFF0A0A0B)
    val PanelLine = Color(0xFF26262A)
    val Label = Color(0xFF6E6E75)
    val DomeCenter = Color(0xFF242427)
    val DomeEdge = Color(0xFF18181B)
    val Inner = Color(0xFF151517)
    val Track = Color(0xFF2E2E32)
    val TrackEnd = Color(0xFF55555B)
    val TorqueTrack = Color(0xFF3A3A3F)
    val Tick = Color(0xFF4E4E55)
    val TickMajor = Color(0xFFDADADE)
    val Text = Color(0xFFEDEDF0)
    val TextSoft = Color(0xFFB9B9BF)
    val Green = Color(0xFF3DDC97)
    val Amber = Color(0xFFF7931E)
    val Red = Color(0xFFF2291E)
    val Cyan = Color(0xFF45E3F5)
    val Pill = Color(0xFF2A2A2D)
    val PillBorder = Color(0xFF414146)
    val HomeButton = Color(0xFFDADADD)
    val Watermark = Color(0xFF2A2A2E)
}

private fun batteryColor(pct: Double?): Color = when {
    pct == null -> RideColors.Label
    pct >= 50 -> RideColors.Green
    pct >= 20 -> RideColors.Amber
    else -> RideColors.Red
}

private enum class Warning { NO_LINK, BATTERY_LOW, BATTERY_CRITICAL, TEMP_WARN, TEMP_DANGER, FAULT }

/**
 * All positions are derived from the screen size so the layout scales across phones.
 * Angles follow Compose's convention: 0° = 3 o'clock, increasing clockwise.
 */
private class RideGeometry(val w: Float, val h: Float) {
    val k = min(h, w / 1.9f)
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

@Composable
fun RideScreen(vm: MainViewModel, onHome: () -> Unit) {
    val telemetry by vm.telemetry.collectAsStateWithLifecycle()
    val vehicle by vm.vehicle.collectAsStateWithLifecycle()
    val modes by vm.modes.collectAsStateWithLifecycle()
    val activeId by vm.activeModeId.collectAsStateWithLifecycle()
    val modeStatus by vm.modeStatus.collectAsStateWithLifecycle()
    val stale by vm.stale.collectAsStateWithLifecycle()
    val rideTimeMs by vm.rideTimeMs.collectAsStateWithLifecycle()

    val activeIndex = modes.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
    val mode = modes.getOrNull(activeIndex)
    val t = telemetry
    val speed = t?.let { Metric.SPEED.value(it, vehicle) }
    val battery = t?.let { Metric.BATTERY.value(it, vehicle) }
    val motorMax = (vehicle.motorCurrentMax * vehicle.controllers).coerceAtLeast(1.0)
    val torqueTarget = t?.let { (it.motorCurrent / motorMax).toFloat().coerceIn(-1f, 1f) } ?: 0f
    val batteryFrac by animateFloatAsState(
        ((battery ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f), tween(500), label = "battery",
    )
    val torqueFrac by animateFloatAsState(torqueTarget, tween(120), label = "torque")
    val batColor = batteryColor(battery)
    val showHome = speed == null || speed < 1.0

    val (fetWarn, fetDanger) = Metric.TEMP_FET.defaultThresholds()!!
    val (motWarn, motDanger) = Metric.TEMP_MOTOR.defaultThresholds()!!
    val warnings = buildList {
        if (t == null || stale) add(Warning.NO_LINK)
        if (battery != null && battery < 10) add(Warning.BATTERY_CRITICAL)
        else if (battery != null && battery < 20) add(Warning.BATTERY_LOW)
        if (t != null && (t.tempFet >= fetDanger || t.tempMotor >= motDanger)) add(Warning.TEMP_DANGER)
        else if (t != null && (t.tempFet >= fetWarn || t.tempMotor >= motWarn)) add(Warning.TEMP_WARN)
        if (t != null && t.fault != 0) add(Warning.FAULT)
    }
    val faultText = t?.fault?.takeIf { it != 0 }?.let { VescProtocol.faultName(it) }

    val pillBorder = when (modeStatus) {
        is ModeApplyStatus.Failed -> RideColors.Red
        is ModeApplyStatus.Applying -> RideColors.Amber
        else -> RideColors.PillBorder
    }
    val boltColor = mode?.let { Color(it.color) } ?: RideColors.TextSoft

    val measurer = rememberTextMeasurer()
    val bolt = rememberVectorPainter(Icons.Filled.Bolt)
    val btOff = rememberVectorPainter(Icons.Filled.BluetoothDisabled)
    val batteryAlert = rememberVectorPainter(Icons.Filled.BatteryAlert)
    val thermo = rememberVectorPainter(Icons.Filled.Thermostat)

    ImmersiveMode()

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(RideColors.Bg),
    ) {
        val g = RideGeometry(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val density = LocalDensity.current

        Canvas(Modifier.fillMaxSize()) {
            drawDome(g)
            drawBatteryGlow(g, batColor, visible = battery != null)
            drawPanels(g, measurer)
            drawPowerMode(g, measurer, bolt, (activeIndex + 1).toString(), boltColor, pillBorder)
            drawBatteryValue(g, measurer, battery, batColor)
            drawRidingTime(g, measurer, rideTimeMs)
            drawBatteryBand(g, batteryFrac, batColor, live = battery != null)
            drawScale(g, measurer)
            drawTorque(g, measurer, bolt, torqueFrac)
            if (!showHome) drawSpeed(g, measurer, speed ?: 0.0, if (vehicle.imperial) "mph" else "kmh")
            drawCentered(
                measurer, AnnotatedString("VESC DASH"),
                wide(0.026f * g.k, RideColors.Watermark, letterSpacing = 0.25f),
                Offset(g.cx, g.watermarkY),
            )
            drawWarnings(g, measurer, warnings, faultText, btOff, batteryAlert, thermo)
        }

        // Tap the power-mode pill to cycle modes.
        with(density) {
            Box(
                Modifier
                    .offset { IntOffset(g.pill.left.roundToInt(), g.pill.top.roundToInt()) }
                    .size(g.pill.width.toDp(), g.pill.height.toDp())
                    .clip(CircleShape)
                    .clickable(enabled = modes.size > 1) {
                        vm.selectMode(modes[(activeIndex + 1) % modes.size].id)
                    },
            )
            if (showHome) {
                Box(
                    Modifier
                        .offset { IntOffset((g.cx - g.homeD / 2f).roundToInt(), (g.homeY - g.homeD / 2f).roundToInt()) }
                        .size(g.homeD.toDp())
                        .clip(CircleShape)
                        .background(RideColors.HomeButton)
                        .clickable(onClick = onHome),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Home,
                        contentDescription = "Back to app",
                        tint = Color(0xFF1A1A1C),
                        modifier = Modifier.size((g.homeD * 0.5f).toDp()),
                    )
                }
            }
        }
    }
}

// ---- drawing ---------------------------------------------------------------

private fun DrawScope.wide(sizePx: Float, color: Color, letterSpacing: Float = 0f) = TextStyle(
    fontFamily = WideFont,
    fontSize = sizePx.toSp(),
    color = color,
    letterSpacing = letterSpacing.em,
)

/** Draws text centred on [anchor]; [alignX] 0 = left edge at anchor, 1 = right edge. */
private fun DrawScope.drawCentered(
    measurer: TextMeasurer,
    text: AnnotatedString,
    style: TextStyle,
    anchor: Offset,
    alignX: Float = 0.5f,
    rotation: Float = 0f,
) {
    val layout = measurer.measure(text, style)
    val topLeft = Offset(anchor.x - layout.size.width * alignX, anchor.y - layout.size.height / 2f)
    if (rotation == 0f) {
        drawText(layout, topLeft = topLeft)
    } else {
        rotate(rotation, pivot = anchor) { drawText(layout, topLeft = topLeft) }
    }
}

private fun DrawScope.drawIcon(p: VectorPainter, center: Offset, size: Float, color: Color) {
    translate(center.x - size / 2f, center.y - size / 2f) {
        with(p) { draw(Size(size, size), colorFilter = ColorFilter.tint(color)) }
    }
}

private fun DrawScope.arc(g: RideGeometry, color: Color, start: Float, sweep: Float, r: Float, width: Float, cap: StrokeCap = StrokeCap.Butt) {
    drawArc(color, start, sweep, false, Offset(g.cx - r, g.cy - r), Size(2 * r, 2 * r), style = Stroke(width, cap = cap))
}

private fun DrawScope.drawDome(g: RideGeometry) {
    val domeR = 1.1f * g.k
    drawCircle(
        Brush.radialGradient(listOf(RideColors.DomeCenter, RideColors.DomeEdge), Offset(g.cx, g.cy), domeR),
        domeR,
        Offset(g.cx, g.cy),
    )
    drawCircle(RideColors.Inner, 0.745f * g.k, Offset(g.cx, g.cy))
}

private fun DrawScope.drawBatteryGlow(g: RideGeometry, color: Color, visible: Boolean) {
    if (!visible) return
    val c = Offset(g.cx, 0.3f * g.h)
    val r = 0.34f * g.h
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.2f), Color.Transparent), c, r), r, c)
}

private fun DrawScope.drawPanels(g: RideGeometry, m: TextMeasurer) {
    val labelStyle = wide(0.024f * g.h, RideColors.Label, 0.08f)
    for (i in 0..2) {
        val left = g.panelLeft(i)
        drawRect(RideColors.Panel.copy(alpha = 0.92f), Offset(left, 0f), Size(g.panelWidth, g.panelBottom))
        drawLine(
            RideColors.PanelLine,
            Offset(left + 0.06f * g.panelWidth, 0.035f * g.h),
            Offset(left + 0.94f * g.panelWidth, 0.035f * g.h),
            strokeWidth = 1f,
        )
        if (i > 0) drawLine(RideColors.PanelLine, Offset(left, 0f), Offset(left, g.panelBottom), strokeWidth = 1f)
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
    drawRoundRect(RideColors.Pill, p.topLeft, p.size, radius)
    drawRoundRect(border, p.topLeft, p.size, radius, style = Stroke(0.006f * g.h))
    val iconSize = 0.085f * g.h
    drawIcon(bolt, Offset(p.left + p.width * 0.34f, p.center.y), iconSize, boltColor)
    drawCentered(m, AnnotatedString(number), wide(0.1f * g.h, RideColors.Text), Offset(p.left + p.width * 0.62f, p.center.y))
}

private fun DrawScope.drawBatteryValue(g: RideGeometry, m: TextMeasurer, battery: Double?, color: Color) {
    val text = buildAnnotatedString {
        withStyle(SpanStyle(fontSize = (0.165f * g.h).toSp())) { append(battery?.roundToInt()?.toString() ?: "--") }
        // Michroma's % glyph reads as "o/o", so use the system face for the sign.
        withStyle(SpanStyle(fontSize = (0.06f * g.h).toSp(), fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold)) {
            append(" %")
        }
    }
    drawCentered(m, text, wide(0.165f * g.h, color), Offset(g.panelCenterX(1), g.valueY))
}

private fun DrawScope.drawRidingTime(g: RideGeometry, m: TextMeasurer, ms: Long) {
    val totalMin = ms / 60_000
    val big = SpanStyle(fontSize = (0.105f * g.h).toSp(), color = RideColors.Text)
    val small = SpanStyle(fontSize = (0.05f * g.h).toSp(), color = RideColors.TextSoft)
    val text = buildAnnotatedString {
        withStyle(big) { append((totalMin / 60).toString()) }
        withStyle(small) { append(" h ") }
        withStyle(big) { append((totalMin % 60).toString().padStart(2, '0')) }
        withStyle(small) { append(" m") }
    }
    drawCentered(m, text, wide(0.105f * g.h, RideColors.Text), Offset(g.panelCenterX(2), g.valueY))
}

private fun DrawScope.drawBatteryBand(g: RideGeometry, frac: Float, color: Color, live: Boolean) {
    // Track: darker at 0, lighter toward 100
    val trackBrush = Brush.sweepGradient(
        0f to RideColors.Track,
        (g.bandStart / 360f) to RideColors.Track,
        ((g.bandStart + g.bandSweep) / 360f) to RideColors.TrackEnd,
        1f to RideColors.TrackEnd,
        center = Offset(g.cx, g.cy),
    )
    drawArc(
        trackBrush, g.bandStart, g.bandSweep, false,
        Offset(g.cx - g.bandR, g.cy - g.bandR), Size(2 * g.bandR, 2 * g.bandR),
        style = Stroke(g.bandW),
    )
    if (live && frac > 0f) {
        arc(g, color.copy(alpha = 0.07f), g.bandStart, g.bandSweep * frac, g.bandR, g.bandW * 1.7f)
        arc(g, color, g.bandStart, g.bandSweep * frac, g.bandR, g.bandW)
    }
    // Fine segmentation across the band
    val inner = g.bandR - g.bandW / 2f
    val outer = g.bandR + g.bandW / 2f
    for (i in 0..100) {
        val a = g.bandStart + g.bandSweep * i / 100f
        drawLine(RideColors.Panel.copy(alpha = 0.45f), g.point(a, inner), g.point(a, outer), strokeWidth = 0.0035f * g.k)
    }
    // Small battery glyph at the start of the band
    val c = g.point(g.bandStart - 3.5f, g.bandR)
    val bw = 0.045f * g.k
    val bh = 0.026f * g.k
    val bodyColor = if (live) color else RideColors.Label
    drawRoundRect(bodyColor, Offset(c.x - bw / 2f, c.y - bh / 2f), Size(bw, bh), CornerRadius(bh * 0.2f), style = Stroke(0.006f * g.k))
    drawRect(bodyColor, Offset(c.x + bw / 2f, c.y - bh * 0.22f), Size(bh * 0.2f, bh * 0.44f))
    if (live) drawRect(bodyColor, Offset(c.x - bw / 2f + bh * 0.2f, c.y - bh * 0.3f), Size((bw - bh * 0.4f) * frac.coerceAtLeast(0.15f), bh * 0.6f))
}

private fun DrawScope.drawScale(g: RideGeometry, m: TextMeasurer) {
    for (i in 0..100) {
        val a = g.bandStart + g.bandSweep * i / 100f
        val major = i % 25 == 0
        val mid = i % 5 == 0
        val len = when {
            major -> 0.05f * g.k
            mid -> 0.034f * g.k
            else -> 0.018f * g.k
        }
        drawLine(
            if (major) RideColors.TickMajor else RideColors.Tick,
            g.point(a, g.tickR),
            g.point(a, g.tickR + len),
            strokeWidth = if (major) 0.005f * g.k else 0.0028f * g.k,
        )
        if (mid) {
            val style = if (major) {
                wide(0.036f * g.k, RideColors.TickMajor)
            } else {
                wide(0.024f * g.k, RideColors.Label)
            }
            drawCentered(m, AnnotatedString(i.toString()), style, g.point(a, g.labelR))
        }
    }
}

private fun DrawScope.drawTorque(g: RideGeometry, m: TextMeasurer, bolt: VectorPainter, frac: Float) {
    arc(g, RideColors.TorqueTrack, g.regAngle, g.maxAngle - g.regAngle, g.torqueR, 0.012f * g.k)
    // Fine ticks just inside the torque track
    var a = g.regAngle
    while (a <= g.maxAngle + 0.01f) {
        drawLine(RideColors.Tick, g.point(a, g.torqueR - 0.045f * g.k), g.point(a, g.torqueR - 0.025f * g.k), strokeWidth = 0.0022f * g.k)
        a += 1.4f
    }
    if (frac > 0.005f) {
        val sweep = (g.maxAngle - g.zeroAngle) * frac
        arc(g, RideColors.Cyan.copy(alpha = 0.18f), g.zeroAngle, sweep, g.torqueR, 0.045f * g.k, StrokeCap.Round)
        arc(g, RideColors.Cyan, g.zeroAngle, sweep, g.torqueR, 0.014f * g.k, StrokeCap.Round)
    } else if (frac < -0.005f) {
        val sweep = (g.regAngle - g.zeroAngle) * -frac
        arc(g, RideColors.Green.copy(alpha = 0.18f), g.zeroAngle, sweep, g.torqueR, 0.045f * g.k, StrokeCap.Round)
        arc(g, RideColors.Green, g.zeroAngle, sweep, g.torqueR, 0.014f * g.k, StrokeCap.Round)
    }
    val labelStyle = wide(0.019f * g.k, RideColors.Label)
    val r = g.torqueR - 0.075f * g.k
    drawCentered(m, AnnotatedString("REG"), labelStyle, g.point(g.regAngle + 1.5f, r), rotation = g.regAngle + 1.5f + 90f)
    drawCentered(m, AnnotatedString("0"), labelStyle, g.point(g.zeroAngle, r), rotation = g.zeroAngle + 90f)
    drawCentered(m, AnnotatedString("MAX"), labelStyle, g.point(g.maxAngle - 2f, r), rotation = g.maxAngle - 2f + 90f)
    drawIcon(bolt, g.point(g.regAngle - 4.5f, g.torqueR - 0.02f * g.k), 0.04f * g.k, RideColors.Label)
}

private fun DrawScope.drawSpeed(g: RideGeometry, m: TextMeasurer, speed: Double, unit: String) {
    drawCentered(m, AnnotatedString(speed.roundToInt().toString()), wide(0.135f * g.k, RideColors.Text), Offset(g.cx, g.speedY))
    drawCentered(m, AnnotatedString(unit), wide(0.036f * g.k, RideColors.Label, 0.05f), Offset(g.cx, g.unitY))
}

private fun DrawScope.drawWarnings(
    g: RideGeometry,
    m: TextMeasurer,
    warnings: List<Warning>,
    faultText: String?,
    btOff: VectorPainter,
    batteryAlert: VectorPainter,
    thermo: VectorPainter,
) {
    val d = 0.1f * g.h
    val x = 0.1f * g.w // clear of a landscape camera cutout
    var y = 0.52f * g.h
    for (w in warnings) {
        val c = Offset(x, y)
        when (w) {
            Warning.NO_LINK -> {
                drawCircle(RideColors.Amber, d / 2f, c, style = Stroke(0.006f * g.h))
                drawIcon(btOff, c, d * 0.58f, RideColors.Amber)
            }
            Warning.BATTERY_LOW -> drawIcon(batteryAlert, c, d, RideColors.Amber)
            Warning.BATTERY_CRITICAL -> drawIcon(batteryAlert, c, d, RideColors.Red)
            Warning.TEMP_WARN -> drawIcon(thermo, c, d, RideColors.Amber)
            Warning.TEMP_DANGER -> drawIcon(thermo, c, d, RideColors.Red)
            Warning.FAULT -> {
                val r = d * 0.36f
                drawCircle(RideColors.Red, r, c, style = Stroke(0.006f * g.h))
                drawLine(RideColors.Red, Offset(c.x - d / 2f, c.y), Offset(c.x - r, c.y), strokeWidth = 0.006f * g.h)
                drawLine(RideColors.Red, Offset(c.x + r, c.y), Offset(c.x + d / 2f, c.y), strokeWidth = 0.006f * g.h)
                drawCentered(m, AnnotatedString("M"), wide(r * 0.95f, RideColors.Red), c)
                if (faultText != null) {
                    drawCentered(m, AnnotatedString(faultText), wide(0.026f * g.h, RideColors.Red), Offset(x + d * 0.7f, y), alignX = 0f)
                }
            }
        }
        y += d * 1.35f
    }
}

// ---- system bars -------------------------------------------------------------

@Composable
private fun ImmersiveMode() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
