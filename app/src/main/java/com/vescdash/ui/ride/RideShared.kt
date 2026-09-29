package com.vescdash.ui.ride

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.em
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vescdash.data.Metric
import com.vescdash.data.ModeApplyStatus
import com.vescdash.data.RideTheme
import com.vescdash.data.defaultThresholds
import com.vescdash.data.value
import com.vescdash.ui.MainViewModel
import com.vescdash.ui.theme.WideFont
import com.vescdash.vesc.VescProtocol
import kotlin.math.roundToInt

/** Colours shared by both ride styles; one instance per theme. */
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
    val pill: Color,
    val pillBorder: Color,
    val homeButton: Color,
    val homeIcon: Color,
    val watermark: Color,
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
            pill = Color(0xFF2A2A2D), pillBorder = Color(0xFF414146),
            homeButton = Color(0xFFDADADD), homeIcon = Color(0xFF1A1A1C), watermark = Color(0xFF2A2A2E),
        )
        val Light = RidePalette(
            bg = Color(0xFFE8E8EC), minimalBg = Color(0xFFF5F5F7),
            panel = Color(0xFFF8F8FA), panelLine = Color(0xFFD6D6DB), label = Color(0xFF7C7C84),
            domeCenter = Color(0xFFFFFFFF), domeEdge = Color(0xFFE2E2E7), inner = Color(0xFFF2F2F5),
            track = Color(0xFFD3D3D8), trackEnd = Color(0xFFB4B4BB), torqueTrack = Color(0xFFCACAD0),
            tick = Color(0xFFA9A9B1), tickMajor = Color(0xFF2A2A2E),
            text = Color(0xFF111114), textSoft = Color(0xFF55555C),
            green = Color(0xFF12B06B), amber = Color(0xFFE07800), red = Color(0xFFDE2318), cyan = Color(0xFF0098B8),
            pill = Color(0xFFE6E6EA), pillBorder = Color(0xFFCDCDD3),
            homeButton = Color(0xFF26262A), homeIcon = Color(0xFFF2F2F4), watermark = Color(0xFFCFCFD4),
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

internal enum class Warning { NO_LINK, BATTERY_LOW, BATTERY_CRITICAL, TEMP_WARN, TEMP_DANGER, FAULT }

/** Everything a ride screen shows, already converted to display units. */
internal class RideData(
    val speed: Double?,
    val speedUnit: String,
    val battery: Double?,
    /** Motor current as a fraction of the max: −1 (full regen) … 1 (full drive). */
    val torque: Float,
    val modeNumber: Int,
    val modeName: String,
    val modeColor: Color?,
    val modeStatus: ModeApplyStatus,
    val rideTimeMs: Long,
    val warnings: List<Warning>,
    val faultText: String?,
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

    val (fetWarn, fetDanger) = Metric.TEMP_FET.defaultThresholds()!!
    val (motWarn, motDanger) = Metric.TEMP_MOTOR.defaultThresholds()!!
    val warnings = buildList {
        if (t == null || stale) add(Warning.NO_LINK)
        if (battery != null && battery < 10) {
            add(Warning.BATTERY_CRITICAL)
        } else if (battery != null && battery < 20) {
            add(Warning.BATTERY_LOW)
        }
        if (t != null && (t.tempFet >= fetDanger || t.tempMotor >= motDanger)) {
            add(Warning.TEMP_DANGER)
        } else if (t != null && (t.tempFet >= fetWarn || t.tempMotor >= motWarn)) {
            add(Warning.TEMP_WARN)
        }
        if (t != null && t.fault != 0) add(Warning.FAULT)
    }

    return RideData(
        speed = t?.let { Metric.SPEED.value(it, vehicle) },
        speedUnit = if (vehicle.imperial) "mph" else "kmh",
        battery = battery,
        torque = t?.let { (it.motorCurrent / motorMax).toFloat().coerceIn(-1f, 1f) } ?: 0f,
        modeNumber = activeIndex + 1,
        modeName = mode?.name.orEmpty(),
        modeColor = mode?.let { Color(it.color) },
        modeStatus = modeStatus,
        rideTimeMs = rideTimeMs,
        warnings = warnings,
        faultText = t?.fault?.takeIf { it != 0 }?.let { VescProtocol.faultName(it) },
    )
}

internal class RideIcons(
    val bolt: VectorPainter,
    val btOff: VectorPainter,
    val batteryAlert: VectorPainter,
    val thermo: VectorPainter,
)

@Composable
internal fun rememberRideIcons() = RideIcons(
    bolt = rememberVectorPainter(Icons.Filled.Bolt),
    btOff = rememberVectorPainter(Icons.Filled.BluetoothDisabled),
    batteryAlert = rememberVectorPainter(Icons.Filled.BatteryAlert),
    thermo = rememberVectorPainter(Icons.Filled.Thermostat),
)

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

internal fun DrawScope.drawIcon(p: VectorPainter, center: Offset, size: Float, color: Color) {
    translate(center.x - size / 2f, center.y - size / 2f) {
        with(p) { draw(Size(size, size), colorFilter = ColorFilter.tint(color)) }
    }
}

/** Vertical stack of warning icons starting at ([x], [y]), each [size] px. */
internal fun DrawScope.drawWarningStack(
    m: TextMeasurer,
    icons: RideIcons,
    c: RidePalette,
    warnings: List<Warning>,
    faultText: String?,
    x: Float,
    y: Float,
    size: Float,
) {
    val stroke = size * 0.06f
    var cy = y
    for (w in warnings) {
        val center = Offset(x, cy)
        when (w) {
            Warning.NO_LINK -> {
                drawCircle(c.amber, size / 2f, center, style = Stroke(stroke))
                drawIcon(icons.btOff, center, size * 0.58f, c.amber)
            }
            Warning.BATTERY_LOW -> drawIcon(icons.batteryAlert, center, size, c.amber)
            Warning.BATTERY_CRITICAL -> drawIcon(icons.batteryAlert, center, size, c.red)
            Warning.TEMP_WARN -> drawIcon(icons.thermo, center, size, c.amber)
            Warning.TEMP_DANGER -> drawIcon(icons.thermo, center, size, c.red)
            Warning.FAULT -> {
                val r = size * 0.36f
                drawCircle(c.red, r, center, style = Stroke(stroke))
                drawLine(c.red, Offset(x - size / 2f, cy), Offset(x - r, cy), strokeWidth = stroke)
                drawLine(c.red, Offset(x + r, cy), Offset(x + size / 2f, cy), strokeWidth = stroke)
                drawCentered(m, AnnotatedString("M"), wide(r * 0.95f, c.red), center)
                if (faultText != null) {
                    drawCentered(m, AnnotatedString(faultText), wide(size * 0.26f, c.red), Offset(x + size * 0.7f, cy), alignX = 0f)
                }
            }
        }
        cy += size * 1.35f
    }
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
