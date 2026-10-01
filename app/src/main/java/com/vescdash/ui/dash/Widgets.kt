package com.vescdash.ui.dash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vescdash.data.ACCEL_START
import com.vescdash.data.AccelRun
import com.vescdash.data.DashWidget
import com.vescdash.data.Metric
import com.vescdash.data.Telemetry
import com.vescdash.data.VehicleSettings
import com.vescdash.data.WidgetSize
import com.vescdash.data.WidgetType
import com.vescdash.data.accelTargets
import com.vescdash.data.defaultRange
import com.vescdash.data.format
import com.vescdash.data.unit
import com.vescdash.data.value
import com.vescdash.ui.common.toFieldText
import com.vescdash.ui.theme.LocalWidgetTheme
import com.vescdash.ui.theme.Palette
import com.vescdash.ui.theme.WidgetTheme
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** A widget with its defaults filled in for the current vehicle. */
data class ResolvedWidget(
    val label: String,
    val unit: String,
    val min: Double,
    val max: Double,
    val warn: Double?,
    val danger: Double?,
)

fun DashWidget.resolve(v: VehicleSettings): ResolvedWidget {
    val range = metric.defaultRange(v)
    return ResolvedWidget(
        label = label?.takeIf { it.isNotBlank() } ?: if (type == WidgetType.ACCEL) "Accel" else metric.label,
        unit = metric.unit(v),
        min = min ?: range.start,
        max = max ?: range.endInclusive,
        warn = warn,
        danger = danger,
    )
}

fun fraction(value: Double?, min: Double, max: Double): Float =
    if (value == null || max <= min) 0f else ((value - min) / (max - min)).toFloat().coerceIn(0f, 1f)

/** Danger below warning means low values are the problem (e.g. battery). */
fun levelColor(value: Double?, warn: Double?, danger: Double?, normal: Color, theme: WidgetTheme): Color {
    if (value == null || (warn == null && danger == null)) return normal
    val lowIsBad = warn != null && danger != null && danger < warn
    fun hit(t: Double?) = t != null && (if (lowIsBad) value <= t else value >= t)
    return when {
        hit(danger) -> theme.danger
        hit(warn) -> theme.warn
        else -> normal
    }
}

fun widgetHeight(w: DashWidget) = when (w.type) {
    WidgetType.NUMBER -> 112.dp
    WidgetType.BAR -> 96.dp
    WidgetType.GRAPH -> 150.dp
    WidgetType.GAUGE -> if (w.size == WidgetSize.FULL) 240.dp else 180.dp
    WidgetType.ACCEL -> 112.dp
}

@Composable
fun WidgetCard(
    widget: DashWidget,
    modifier: Modifier = Modifier,
    telemetry: Telemetry?,
    history: List<Telemetry>,
    historyCapacity: Int,
    vehicle: VehicleSettings,
    editing: Boolean,
    onEdit: () -> Unit,
    onMoveBack: () -> Unit,
    onMoveForward: () -> Unit,
    onToggleSize: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = Palette.cardShape

    Box(
        modifier
            .fillMaxWidth()
            .height(widgetHeight(widget))
            .clip(shape)
            .background(Palette.Surface)
            .then(if (editing) Modifier.border(1.dp, Palette.Outline, shape) else Modifier),
    ) {
        WidgetBody(widget, telemetry, history, historyCapacity, vehicle)
        AnimatedVisibility(
            visible = editing,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.96f),
            exit = fadeOut(tween(150)),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))) {
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    EditButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Move back", onMoveBack)
                    EditButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Move forward", onMoveForward)
                    EditButton(
                        if (widget.size == WidgetSize.FULL) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        "Toggle size",
                        onToggleSize,
                    )
                    EditButton(Icons.Filled.Edit, "Edit", onEdit)
                    EditButton(Icons.Filled.Delete, "Delete", onDelete, tint = Palette.Danger)
                }
            }
        }
    }
}

/** A widget's content without the Dash tab's card and edit overlay; the ride screen draws these directly. */
@Composable
fun WidgetBody(
    widget: DashWidget,
    telemetry: Telemetry?,
    history: List<Telemetry>,
    historyCapacity: Int,
    vehicle: VehicleSettings,
    accel: AccelRun = AccelRun(),
) {
    val accent = MaterialTheme.colorScheme.primary
    val r = widget.resolve(vehicle)
    val value = telemetry?.let { widget.metric.value(it, vehicle) }
    when (widget.type) {
        WidgetType.NUMBER -> NumberWidget(r, widget.metric, value, big = widget.size == WidgetSize.FULL)
        WidgetType.GAUGE -> GaugeWidget(r, widget.metric, value, accent)
        WidgetType.BAR -> BarWidget(r, widget.metric, value, accent)
        WidgetType.GRAPH -> GraphWidget(widget, r, value, history, historyCapacity, vehicle, accent)
        WidgetType.ACCEL -> AccelWidget(widget, r, value, accel)
    }
}

@Composable
private fun EditButton(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = Palette.Fg) {
    // Stark's grid has narrower cards, so its buttons are a little smaller to fit all five.
    IconButton(onClick = onClick, modifier = if (Palette.stark) Modifier.size(34.dp) else Modifier) { Icon(icon, contentDescription = description, tint = tint) }
}

@Composable
private fun WidgetLabel(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 11.sp,
    color: Color = LocalWidgetTheme.current.dim,
    tight: Boolean = false,
) {
    val theme = LocalWidgetTheme.current
    Text(
        text.uppercase(),
        modifier = modifier,
        color = color,
        fontSize = size,
        style = LocalTextStyle.current.merge(theme.label).let { if (tight) it.tightTo(size) else it },
        maxLines = 1,
    )
}

/** The wide face has a very tall line box; this makes it only as tall as [size], for text that has to share a small widget. */
private fun TextStyle.tightTo(size: TextUnit) = copy(
    lineHeight = size,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

@Composable
private fun NumberWidget(r: ResolvedWidget, metric: Metric, value: Double?, big: Boolean) {
    val theme = LocalWidgetTheme.current
    val color = levelColor(value, r.warn, r.danger, theme.fg, theme)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val u = minOf(maxWidth.value, maxHeight.value)
        val padH = theme.scale(16f, u, 0.1f)
        val unitSize = theme.scale(14f, u, 0.1f, min = 9f)
        val numberSize = if (theme.scaleToBox) {
            // The wide face runs about 0.95 em a digit: cap the size so the biggest expected reading and unit still fit.
            val chars = max(2, metric.format(r.max).length) + 1
            min(0.5f * u, (maxWidth.value - 2 * padH - unitSize * (r.unit.length * 0.6f + 1f)) / (chars * 0.95f))
        } else {
            (if (big) 54f else 40f) * theme.digitScale
        }
        Column(Modifier.fillMaxSize().padding(horizontal = padH.dp, vertical = theme.scale(12f, u, 0.08f).dp)) {
            WidgetLabel(r.label, size = theme.labelSize(u, 0.11f).sp)
            Spacer(Modifier.weight(1f))
            // On the ride screen the reading sits in the middle of its box; on the Dash tab, bottom left.
            Row(
                if (theme.scaleToBox) Modifier.fillMaxWidth() else Modifier,
                horizontalArrangement = if (theme.scaleToBox) Arrangement.Center else Arrangement.Start,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    metric.format(value),
                    style = theme.number,
                    color = color,
                    fontSize = numberSize.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
                Spacer(Modifier.width(theme.scale(6f, u, 0.04f).dp))
                Text(
                    r.unit,
                    color = theme.dim,
                    fontSize = unitSize.sp,
                    modifier = Modifier.padding(bottom = theme.scale(8f, numberSize, 0.2f).dp),
                )
            }
            if (theme.scaleToBox) Spacer(Modifier.weight(1f))
        }
    }
}

/** [speed] is the live speed: the widget only invites a tap while stopped. [accel] counts if it's this widget's run. */
@Composable
private fun AccelWidget(widget: DashWidget, r: ResolvedWidget, speed: Double?, accel: AccelRun) {
    val theme = LocalWidgetTheme.current
    val run = accel.takeIf { it.id == widget.id } ?: AccelRun()
    val targets = run.targets.ifEmpty { widget.accelTargets() }
    val armed = run.state == AccelRun.State.ARMED
    val running = run.state == AccelRun.State.RUNNING
    val status = when {
        armed -> "READY"
        !running && (speed == null || speed < ACCEL_START) -> "TAP TO ARM"
        else -> ""
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val u = minOf(maxWidth.value, maxHeight.value)
        val padH = theme.scale(16f, u, 0.1f)
        val padV = theme.scale(12f, u, 0.08f)
        val labelSize = theme.labelSize(u, 0.11f)
        val rowH = (maxHeight.value - 2 * padV - labelSize) / targets.size
        val width = maxWidth.value
        val rangeSize = (0.2f * rowH).coerceIn(9f, 13f)
        Column(Modifier.fillMaxSize().padding(horizontal = padH.dp, vertical = padV.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                WidgetLabel(r.label, Modifier.weight(1f), labelSize.sp, tight = true)
                WidgetLabel(status, size = labelSize.sp, color = if (armed) theme.warn else theme.dim, tight = true)
            }
            targets.forEachIndexed { i, target ->
                // Reached: the split. Being chased: the clock, ticking with each sample. Otherwise nothing yet.
                val ms = run.splitsMs.getOrNull(i) ?: if (running && i == run.splitsMs.size) run.last?.let { it.first - run.startMs } else null
                val text = ms?.let { String.format(Locale.US, "%.2f", it / 1000.0) } ?: "--"
                // The wide face runs about 0.95 em a digit.
                val numberSize = min(0.5f * rowH, (width - 2 * padH - 2 * rangeSize) / (max(4, text.length) * 0.95f))
                Column(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    WidgetLabel("0–${target.toFieldText()} ${r.unit}", size = rangeSize.sp, tight = true)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text,
                            style = theme.number.tightTo(numberSize.sp),
                            color = if (ms == null) theme.dim else theme.fg,
                            fontSize = numberSize.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(theme.scale(4f, u, 0.04f).dp))
                        Text("s", color = theme.dim, fontSize = rangeSize.sp, style = LocalTextStyle.current.tightTo(rangeSize.sp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GaugeWidget(r: ResolvedWidget, metric: Metric, value: Double?, accent: Color) {
    val theme = LocalWidgetTheme.current
    val target = fraction(value, r.min, r.max)
    val animated by animateFloatAsState(target, animationSpec = tween(150), label = "gauge")
    val color = levelColor(value, r.warn, r.danger, accent, theme)

    BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp)) {
        val u = minOf(maxWidth.value, maxHeight.value)
        val numberSize = (u * 0.24f * theme.digitScale).sp
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.085f
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f + diameter * 0.08f)
            val arcSize = Size(diameter, diameter)
            drawArc(theme.outline, 150f, 240f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (animated > 0.002f) {
                if (theme.glow) {
                    drawArc(color.copy(alpha = 0.2f), 150f, 240f * animated, false, topLeft, arcSize, style = Stroke(stroke * 2f, cap = StrokeCap.Round))
                }
                drawArc(color, 150f, 240f * animated, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                metric.format(value),
                style = theme.number,
                color = theme.fg,
                fontSize = numberSize,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            Text(r.unit, color = theme.dim, fontSize = theme.scale(14f, u, 0.07f, min = 9f).sp)
        }
        WidgetLabel(r.label, Modifier.align(Alignment.BottomCenter), theme.labelSize(u, 0.06f).sp)
    }
}

@Composable
private fun BarWidget(r: ResolvedWidget, metric: Metric, value: Double?, accent: Color) {
    val theme = LocalWidgetTheme.current
    val target = fraction(value, r.min, r.max)
    val animated by animateFloatAsState(target, animationSpec = tween(150), label = "bar")
    val color = levelColor(value, r.warn, r.danger, accent, theme)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val u = minOf(maxWidth.value, maxHeight.value)
        val barHeight = theme.scale(12f, u, 0.16f).dp
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = theme.scale(16f, u, 0.2f).dp, vertical = theme.scale(14f, u, 0.16f).dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                WidgetLabel(r.label, Modifier.weight(1f).padding(bottom = 4.dp), theme.labelSize(u, 0.15f).sp)
                Text(
                    metric.format(value),
                    style = theme.number,
                    color = theme.fg,
                    fontSize = (theme.scale(26f, u, 0.3f) * theme.digitScale).sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    r.unit,
                    color = theme.dim,
                    fontSize = theme.scale(12f, u, 0.15f, min = 9f).sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(RoundedCornerShape(barHeight / 2))
                    .background(theme.outline),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(animated)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(barHeight / 2))
                        .background(color),
                )
            }
        }
    }
}

@Composable
private fun GraphWidget(
    widget: DashWidget,
    r: ResolvedWidget,
    value: Double?,
    history: List<Telemetry>,
    capacity: Int,
    vehicle: VehicleSettings,
    accent: Color,
) {
    val theme = LocalWidgetTheme.current
    val points = remember(history, vehicle, widget.metric) { history.mapNotNull { widget.metric.value(it, vehicle) } }
    val color = levelColor(value, r.warn, r.danger, accent, theme)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val u = minOf(maxWidth.value, maxHeight.value)
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = theme.scale(16f, u, 0.2f).dp, vertical = theme.scale(12f, u, 0.12f).dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                WidgetLabel(r.label, Modifier.weight(1f).padding(bottom = 3.dp), theme.labelSize(u, 0.15f).sp)
                Text(
                    widget.metric.format(value),
                    style = theme.number,
                    color = theme.fg,
                    fontSize = (theme.scale(22f, u, 0.24f) * theme.digitScale).sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    r.unit,
                    color = theme.dim,
                    fontSize = theme.scale(12f, u, 0.15f, min = 9f).sp,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            Spacer(Modifier.height(theme.scale(8f, u, 0.08f).dp))
            Canvas(Modifier.fillMaxWidth().weight(1f)) {
                if (points.size < 2) return@Canvas
                // Auto-scale to the data unless the user pinned min/max.
                val lo = widget.min ?: minOf(points.min(), 0.0)
                var hi = widget.max ?: points.max()
                if (hi - lo < 1e-6) hi = lo + 1.0
                val n = max(points.size, capacity)
                val stepX = size.width / (n - 1)
                val startIndex = n - points.size
                fun x(i: Int) = (startIndex + i) * stepX
                fun y(v: Double) = (size.height - ((v - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f) * size.height)

                if (lo < 0 && hi > 0) {
                    val zy = y(0.0)
                    drawLine(theme.outline, Offset(0f, zy), Offset(size.width, zy), strokeWidth = 1.dp.toPx())
                }
                val line = Path().apply {
                    points.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
                }
                val baseY = y(max(lo, 0.0).coerceAtMost(hi))
                val fill = Path().apply {
                    addPath(line)
                    lineTo(x(points.lastIndex), baseY)
                    lineTo(x(0), baseY)
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0f))))
                if (theme.glow) drawPath(line, color.copy(alpha = 0.25f), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
