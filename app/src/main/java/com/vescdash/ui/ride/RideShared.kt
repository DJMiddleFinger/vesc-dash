package com.vescdash.ui.ride

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vescdash.data.Metric
import com.vescdash.data.ModeApplyStatus
import com.vescdash.data.RideTheme
import com.vescdash.data.defaultThresholds
import com.vescdash.data.unit
import com.vescdash.data.value
import com.vescdash.ui.MainViewModel
import com.vescdash.ui.theme.WideFont
import com.vescdash.vesc.VescProtocol
import kotlin.math.abs
import kotlin.math.roundToInt

/** Colours shared by all ride styles; one instance per theme. */
internal class RidePalette(
    val bg: Color,
    val minimalBg: Color,
    val panel: Color,
    val panelLine: Color,
    val label: Color,
    val domeCenter: Color,
    val domeEdge: Color,
    val inner: Color,
    val track: Color,
    val trackEnd: Color,
    val torqueTrack: Color,
    val tick: Color,
    val tickMajor: Color,
    val text: Color,
    val textSoft: Color,
    val green: Color,
    val amber: Color,
    val red: Color,
    val cyan: Color,
    /** Colour of a heat warning at 60 °C; blends toward [red] as it gets hotter. */
    val heatStart: Color,
    val pill: Color,
    val pillBorder: Color,
    val homeButton: Color,
    val homeIcon: Color,
    val watermark: Color,
    val tilesBgTop: Color,
    val tilesBgBottom: Color,
    val tile: Color,
    val tilePill: Color,
    /** Text/icons drawn on top of a battery-coloured pill. */
    val onAccent: Color,
) {
    companion object {
        val Dark = RidePalette(
            bg = Color(0xFF111113), minimalBg = Color(0xFF000000),
            panel = Color(0xFF0A0A0B), panelLine = Color(0xFF26262A), label = Color(0xFF6E6E75),
            domeCenter = Color(0xFF242427), domeEdge = Color(0xFF18181B), inner = Color(0xFF151517),
            track = Color(0xFF2E2E32), trackEnd = Color(0xFF55555B), torqueTrack = Color(0xFF3A3A3F),
            tick = Color(0xFF4E4E55), tickMajor = Color(0xFFDADADE),
            text = Color(0xFFEDEDF0), textSoft = Color(0xFFB9B9BF),
            green = Color(0xFF3DDC97), amber = Color(0xFFF7931E), red = Color(0xFFF2291E), cyan = Color(0xFF45E3F5),
            heatStart = Color(0xFFFFD60A),
            pill = Color(0xFF2A2A2D), pillBorder = Color(0xFF414146),
            homeButton = Color(0xFFDADADD), homeIcon = Color(0xFF1A1A1C), watermark = Color(0xFF2A2A2E),
            tilesBgTop = Color(0xFF131416), tilesBgBottom = Color(0xFF060607),
            tile = Color(0xFF1C1D20), tilePill = Color(0xFF2D2E32), onAccent = Color(0xFF0B0B0C),
        )
        val Light = RidePalette(
            bg = Color(0xFFE8E8EC), minimalBg = Color(0xFFF5F5F7),
            panel = Color(0xFFF8F8FA), panelLine = Color(0xFFD6D6DB), label = Color(0xFF7C7C84),
            domeCenter = Color(0xFFFFFFFF), domeEdge = Color(0xFFE2E2E7), inner = Color(0xFFF2F2F5),
            track = Color(0xFFD3D3D8), trackEnd = Color(0xFFB4B4BB), torqueTrack = Color(0xFFCACAD0),
            tick = Color(0xFFA9A9B1), tickMajor = Color(0xFF2A2A2E),
            text = Color(0xFF111114), textSoft = Color(0xFF55555C),
            green = Color(0xFF12B06B), amber = Color(0xFFE07800), red = Color(0xFFDE2318), cyan = Color(0xFF0098B8),
            heatStart = Color(0xFFD9A400),
            pill = Color(0xFFE6E6EA), pillBorder = Color(0xFFCDCDD3),
            homeButton = Color(0xFF26262A), homeIcon = Color(0xFFF2F2F4), watermark = Color(0xFFCFCFD4),
            tilesBgTop = Color(0xFFF4F4F6), tilesBgBottom = Color(0xFFE4E4E8),
            tile = Color(0xFFFFFFFF), tilePill = Color(0xFFECECEF), onAccent = Color(0xFFFFFFFF),
        )
    }
}

@Composable
internal fun ridePalette(theme: RideTheme): RidePalette = when (theme) {
    RideTheme.DARK -> RidePalette.Dark
    RideTheme.LIGHT -> RidePalette.Light
    RideTheme.SYSTEM -> if (isSystemInDarkTheme()) RidePalette.Dark else RidePalette.Light
}

/** Stark's battery colours: green ≥ 50 %, amber ≥ 20 %, red below. */
internal fun batteryColor(pct: Double?, c: RidePalette): Color = when {
    pct == null -> c.label
    pct >= 50 -> c.green
    pct >= 20 -> c.amber
    else -> c.red
}

internal sealed interface RideWarning {
    data object NoLink : RideWarning
    data class LowBattery(val critical: Boolean) : RideWarning
    data class ControllerHot(val tempC: Double, val startC: Double) : RideWarning
    data class MotorHot(val tempC: Double, val startC: Double) : RideWarning
    data class Fault(val text: String) : RideWarning
}

/**
 * Heat icons appear in yellow at the temperature set in Setup and are fully red (and
 * pulsing) at the same danger points the dashboard uses.
 */
internal val CONTROLLER_RED_C = Metric.TEMP_FET.defaultThresholds()!!.second
internal val MOTOR_RED_C = Metric.TEMP_MOTOR.defaultThresholds()!!.second

/** 0 at [startC], 1 at [redAtC] (or immediately, if the icon is set to appear past red). */
internal fun heatFraction(tempC: Double, startC: Double, redAtC: Double): Float =
    if (redAtC <= startC) 1f else ((tempC - startC) / (redAtC - startC)).toFloat().coerceIn(0f, 1f)

/**
 * Yellow at [startC], shifting to red at [redAtC]. The blend is front-loaded so the icon is
 * already orange-red about a quarter of the way to the danger point.
 */
internal fun heatColor(tempC: Double, startC: Double, redAtC: Double, c: RidePalette): Color {
    val f = heatFraction(tempC, startC, redAtC)
    val k = 1f - f
    val eased = 1f - k * k * k * k
    // Straight channel blend: yellow → orange → red, reaching red sooner than a perceptual blend.
    return Color(
        red = c.heatStart.red + (c.red.red - c.heatStart.red) * eased,
        green = c.heatStart.green + (c.red.green - c.heatStart.green) * eased,
        blue = c.heatStart.blue + (c.red.blue - c.heatStart.blue) * eased,
    )
}

/** Everything a ride screen shows, already converted to display units. */
internal class RideData(
    val speed: Double?,
    val speedUnit: String,
    val trip: Double?,
    val tripUnit: String,
    val motorTempC: Double?,
    val battery: Double?,
    /** Motor current as a fraction of the max: −1 (full regen) … 1 (full drive). */
    val torque: Float,
    val modeNumber: Int,
    val modeName: String,
    val modeColor: Color?,
    val modeStatus: ModeApplyStatus,
    val rideTimeMs: Long,
    val warnings: List<RideWarning>,
) {
    val stopped: Boolean get() = speed == null || speed < 1.0
}

@Composable
internal fun collectRideData(vm: MainViewModel): RideData {
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
    val battery = t?.let { Metric.BATTERY.value(it, vehicle) }
    val motorMax = (vehicle.motorCurrentMax * vehicle.controllers).coerceAtLeast(1.0)

    val warnings = buildList {
        if (t == null || stale) add(RideWarning.NoLink)
        if (battery != null && battery < 20) add(RideWarning.LowBattery(critical = battery < 10))
        t?.tempFet?.let { if (it >= vehicle.heatWarnControllerC) add(RideWarning.ControllerHot(it, vehicle.heatWarnControllerC)) }
        t?.tempMotor?.let { if (it >= vehicle.heatWarnMotorC) add(RideWarning.MotorHot(it, vehicle.heatWarnMotorC)) }
        if (t != null && t.fault != 0) add(RideWarning.Fault(VescProtocol.faultName(t.fault)))
    }

    return RideData(
        speed = t?.let { Metric.SPEED.value(it, vehicle) },
        speedUnit = if (vehicle.imperial) "mph" else "kmh",
        trip = t?.let { Metric.TRIP.value(it, vehicle) },
        tripUnit = Metric.TRIP.unit(vehicle),
        motorTempC = t?.tempMotor,
        battery = battery,
        torque = t?.let { (it.motorCurrent / motorMax).toFloat().coerceIn(-1f, 1f) } ?: 0f,
        modeNumber = activeIndex + 1,
        modeName = mode?.name.orEmpty(),
        modeColor = mode?.let { Color(it.color) },
        modeStatus = modeStatus,
        rideTimeMs = rideTimeMs,
        warnings = warnings,
    )
}

internal class RideIcons(
    val bolt: VectorPainter,
    val btOff: VectorPainter,
    val batteryAlert: VectorPainter,
    val thermo: VectorPainter,
    val chip: VectorPainter,
)

@Composable
internal fun rememberRideIcons() = RideIcons(
    bolt = rememberVectorPainter(Icons.Filled.Bolt),
    btOff = rememberVectorPainter(Icons.Filled.BluetoothDisabled),
    batteryAlert = rememberVectorPainter(Icons.Filled.BatteryAlert),
    thermo = rememberVectorPainter(Icons.Filled.Thermostat),
    chip = rememberVectorPainter(Icons.Filled.Memory),
)

/** 0.35 ↔ 1 blink used by warnings that have hit their red point. */
@Composable
internal fun rememberWarningPulse(): Float {
    val transition = rememberInfiniteTransition(label = "warningPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
        label = "pulse",
    )
    return pulse
}

internal fun pillBorderColor(status: ModeApplyStatus, c: RidePalette): Color = when (status) {
    is ModeApplyStatus.Failed -> c.red
    is ModeApplyStatus.Applying -> c.amber
    else -> c.pillBorder
}

// ---- start-up animation -------------------------------------------------------

/** Runs 0 → 1 once when a ride screen appears; drives its start-up sweep. */
@Composable
internal fun rememberIntroProgress(): Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(1400, easing = LinearEasing)) }
    return progress.value
}

/** Like a car cluster at key-on: sweep up to full, then settle onto [actual]. */
internal fun introSweep(progress: Float, actual: Float): Float = when {
    progress >= 1f -> actual
    progress < 0.5f -> FastOutSlowInEasing.transform(progress / 0.5f)
    else -> 1f + (actual - 1f) * FastOutSlowInEasing.transform((progress - 0.5f) / 0.5f)
}

/** Eased 0 → 1 for the part of the intro between [start] and [end]. */
internal fun introPhase(progress: Float, start: Float, end: Float): Float =
    FastOutSlowInEasing.transform(((progress - start) / (end - start)).coerceIn(0f, 1f))

// ---- shared controls -------------------------------------------------------------

/** Invisible tap target laid over something drawn on the canvas. */
@Composable
internal fun TapTarget(rect: Rect, enabled: Boolean, onClick: () -> Unit) {
    val density = LocalDensity.current
    Box(
        Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
internal fun HomeButton(visible: Boolean, center: Offset, diameter: Float, c: RidePalette, onClick: () -> Unit) {
    val density = LocalDensity.current
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.offset {
            IntOffset((center.x - diameter / 2f).roundToInt(), (center.y - diameter / 2f).roundToInt())
        },
        enter = fadeIn(tween(250)) + scaleIn(tween(300), initialScale = 0.6f),
        exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.6f),
    ) {
        Box(
            Modifier
                .size(with(density) { diameter.toDp() })
                .clip(CircleShape)
                .background(c.homeButton)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Home,
                contentDescription = "Back to app",
                tint = c.homeIcon,
                modifier = Modifier.size(with(density) { (diameter * 0.5f).toDp() }),
            )
        }
    }
}

// ---- drawing helpers --------------------------------------------------------------

internal fun DrawScope.wide(sizePx: Float, color: Color, letterSpacing: Float = 0f) = TextStyle(
    fontFamily = WideFont,
    fontSize = sizePx.toSp(),
    color = color,
    letterSpacing = letterSpacing.em,
)

/** Draws text centred on [anchor]; [alignX] 0 = left edge at anchor, 1 = right edge. */
internal fun DrawScope.drawCentered(
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

/**
 * Horizontal power/regen bar from [x0] to [x1]: zero sits 15 % in from the left, drive fills
 * right in cyan, regen fills left in green. [frac] is −1 (full regen) … 1 (full drive).
 */
internal fun DrawScope.drawPowerBar(
    m: TextMeasurer,
    c: RidePalette,
    frac: Float,
    y: Float,
    x0: Float,
    x1: Float,
    thickness: Float,
    labelSize: Float,
) {
    val zeroX = x0 + 0.15f * (x1 - x0)
    val th = thickness
    drawRoundRect(c.track, Offset(x0, y - th / 2f), Size(x1 - x0, th), CornerRadius(th / 2f))
    if (abs(frac) > 0.003f) {
        val (start, end, color) = if (frac > 0f) {
            Triple(zeroX, zeroX + (x1 - zeroX) * frac, c.cyan)
        } else {
            Triple(zeroX - (zeroX - x0) * -frac, zeroX, c.green)
        }
        drawRoundRect(color.copy(alpha = 0.18f), Offset(start - th, y - th * 1.5f), Size(end - start + 2 * th, th * 3f), CornerRadius(th * 1.5f))
        drawRoundRect(color, Offset(start, y - th / 2f), Size(end - start, th), CornerRadius(th / 2f))
    }
    drawLine(c.textSoft, Offset(zeroX, y - th * 1.6f), Offset(zeroX, y + th * 1.6f), strokeWidth = th * 0.25f)
    val labelStyle = wide(labelSize, c.label, 0.05f)
    val gap = labelSize * 0.9f
    drawCentered(m, AnnotatedString("REG"), labelStyle, Offset(x0 - gap, y), alignX = 1f)
    drawCentered(m, AnnotatedString("MAX"), labelStyle, Offset(x1 + gap, y), alignX = 0f)
}

/** Michroma draws % and ° badly ("o/o", a small "o"), so units use the system face. */
internal fun AnnotatedString.Builder.appendUnit(
    text: String,
    fontSize: TextUnit = TextUnit.Unspecified,
    weight: FontWeight = FontWeight.SemiBold,
) {
    withStyle(SpanStyle(fontFamily = FontFamily.Default, fontWeight = weight, fontSize = fontSize)) { append(text) }
}

/** Riding time as "0 h 17 m": digits in [big], units in [small]. */
internal fun ridingTimeText(ms: Long, big: SpanStyle, small: SpanStyle): AnnotatedString {
    val totalMin = ms / 60_000
    return buildAnnotatedString {
        withStyle(big) { append((totalMin / 60).toString()) }
        withStyle(small) { append(" h ") }
        withStyle(big) { append((totalMin % 60).toString().padStart(2, '0')) }
        withStyle(small) { append(" m") }
    }
}

internal fun DrawScope.drawIcon(p: VectorPainter, center: Offset, size: Float, color: Color) {
    translate(center.x - size / 2f, center.y - size / 2f) {
        with(p) { draw(Size(size, size), colorFilter = ColorFilter.tint(color)) }
    }
}

/**
 * Stack of warning icons starting at ([x], [y]), each [size] px — downward, or left-to-right
 * when [horizontal]. Heat warnings show the part (chip = controller, motor outline = motor)
 * with a thermometer badge and the temperature, coloured yellow → red; at their red point
 * they blink with [pulse].
 */
internal fun DrawScope.drawWarningStack(
    m: TextMeasurer,
    icons: RideIcons,
    c: RidePalette,
    warnings: List<RideWarning>,
    x: Float,
    y: Float,
    size: Float,
    pulse: Float,
    horizontal: Boolean = false,
) {
    val stroke = size * 0.06f
    var cx = x
    var cy = y
    for (w in warnings) {
        val center = Offset(cx, cy)
        when (w) {
            RideWarning.NoLink -> {
                drawCircle(c.amber, size / 2f, center, style = Stroke(stroke))
                drawIcon(icons.btOff, center, size * 0.58f, c.amber)
            }
            is RideWarning.LowBattery -> drawIcon(icons.batteryAlert, center, size, if (w.critical) c.red else c.amber)
            is RideWarning.ControllerHot -> drawHeatWarning(m, icons, c, center, size, w.tempC, w.startC, CONTROLLER_RED_C, "CTRL", pulse) { color ->
                drawIcon(icons.chip, center, size * 0.9f, color)
            }
            is RideWarning.MotorHot -> drawHeatWarning(m, icons, c, center, size, w.tempC, w.startC, MOTOR_RED_C, "MOTOR", pulse) { color ->
                drawMotorGlyph(center, size * 0.9f, color)
            }
            is RideWarning.Fault -> {
                val r = size * 0.36f
                drawCircle(c.red, r, center, style = Stroke(stroke))
                drawLine(c.red, Offset(cx - size / 2f, cy), Offset(cx - r, cy), strokeWidth = stroke)
                drawLine(c.red, Offset(cx + r, cy), Offset(cx + size / 2f, cy), strokeWidth = stroke)
                drawCentered(m, AnnotatedString("M"), wide(r * 0.95f, c.red), center)
                drawCentered(m, AnnotatedString(w.text), wide(size * 0.26f, c.red), Offset(cx + size * 0.7f, cy), alignX = 0f)
            }
        }
        if (horizontal) {
            // Heat warnings carry a temperature to their right, so they need more room.
            cx += size * when (w) {
                is RideWarning.ControllerHot, is RideWarning.MotorHot -> 3.1f
                else -> 1.5f
            }
        } else {
            cy += size * 1.35f
        }
    }
}

private fun DrawScope.drawHeatWarning(
    m: TextMeasurer,
    icons: RideIcons,
    c: RidePalette,
    center: Offset,
    size: Float,
    tempC: Double,
    startC: Double,
    redAtC: Double,
    label: String,
    pulse: Float,
    glyph: DrawScope.(Color) -> Unit,
) {
    val alpha = if (tempC >= redAtC) pulse else 1f
    val color = heatColor(tempC, startC, redAtC, c).copy(alpha = alpha)
    glyph(color)
    // Thermometer badge in the lower-right corner
    val badge = size * 0.5f
    val badgeCenter = Offset(center.x + size * 0.38f, center.y + size * 0.3f)
    drawCircle(c.minimalBg.copy(alpha = 0.85f), badge * 0.45f, badgeCenter)
    drawIcon(icons.thermo, badgeCenter, badge, color)
    // Temperature and label to the right
    val textX = center.x + size * 0.78f
    val temp = buildAnnotatedString {
        append(tempC.roundToInt().toString())
        appendUnit("°C", (size * 0.28f).toSp())
    }
    drawCentered(m, temp, wide(size * 0.4f, color), Offset(textX, center.y - size * 0.1f), alignX = 0f)
    drawCentered(m, AnnotatedString(label), wide(size * 0.16f, c.label.copy(alpha = alpha), 0.08f), Offset(textX, center.y + size * 0.3f), alignX = 0f)
}

/** Simple electric-motor pictogram: finned body, end cap and shaft. */
private fun DrawScope.drawMotorGlyph(center: Offset, size: Float, color: Color) {
    val stroke = size * 0.08f
    val bodyW = size * 0.62f
    val bodyH = size * 0.46f
    val left = center.x - size * 0.4f
    val top = center.y - bodyH / 2f + size * 0.04f
    drawRoundRect(color, Offset(left, top), Size(bodyW, bodyH), CornerRadius(size * 0.06f), style = Stroke(stroke))
    // cooling fins on top
    for (i in 0..2) {
        val fx = left + bodyW * (0.22f + 0.28f * i)
        drawLine(color, Offset(fx, top), Offset(fx, top - size * 0.14f), strokeWidth = stroke)
    }
    // end cap and shaft
    drawRect(color, Offset(left + bodyW, center.y - bodyH * 0.28f + size * 0.04f), Size(size * 0.1f, bodyH * 0.56f))
    drawLine(color, Offset(left + bodyW + size * 0.1f, center.y + size * 0.04f), Offset(left + bodyW + size * 0.28f, center.y + size * 0.04f), strokeWidth = stroke)
    // base/feet
    drawLine(color, Offset(left + bodyW * 0.1f, top + bodyH + size * 0.1f), Offset(left + bodyW * 0.9f, top + bodyH + size * 0.1f), strokeWidth = stroke)
}

// ---- system bars ------------------------------------------------------------------

@Composable
internal fun ImmersiveMode() {
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
