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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vescdash.data.DashWidget
import com.vescdash.data.Metric
import com.vescdash.data.Telemetry
import com.vescdash.data.VehicleSettings
import com.vescdash.data.WidgetSize
import com.vescdash.data.WidgetType
import com.vescdash.data.defaultRange
import com.vescdash.data.format
import com.vescdash.data.unit
import com.vescdash.data.value
import com.vescdash.ui.theme.NumberStyle
import com.vescdash.ui.theme.Palette
import kotlin.math.max

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
        label = label?.takeIf { it.isNotBlank() } ?: metric.label,
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
fun levelColor(value: Double?, warn: Double?, danger: Double?, normal: Color): Color {
    if (value == null || (warn == null && danger == null)) return normal
    val lowIsBad = warn != null && danger != null && danger < warn
    fun hit(t: Double?) = t != null && (if (lowIsBad) value <= t else value >= t)
    return when {
        hit(danger) -> Palette.Danger
        hit(warn) -> Palette.Warn
        else -> normal
    }
}

fun widgetHeight(w: DashWidget) = when (w.type) {
    WidgetType.NUMBER -> 112.dp
    WidgetType.BAR -> 96.dp
    WidgetType.GRAPH -> 150.dp
    WidgetType.GAUGE -> if (w.size == WidgetSize.FULL) 240.dp else 180.dp
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
    val shape = RoundedCornerShape(20.dp)
    val accent = MaterialTheme.colorScheme.primary
    val r = widget.resolve(vehicle)
    val value = telemetry?.let { widget.metric.value(it, vehicle) }

    Box(
        modifier
            .fillMaxWidth()
            .height(widgetHeight(widget))
            .clip(shape)
            .background(Palette.Surface)
            .then(if (editing) Modifier.border(1.dp, Palette.Outline, shape) else Modifier),
    ) {
        when (widget.type) {
            WidgetType.NUMBER -> NumberWidget(r, widget.metric, value, big = widget.size == WidgetSize.FULL)
            WidgetType.GAUGE -> GaugeWidget(r, widget.metric, value, accent)
            WidgetType.BAR -> BarWidget(r, widget.metric, value, accent)
            WidgetType.GRAPH -> GraphWidget(widget, r, value, history, historyCapacity, vehicle, accent)
        }
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

@Composable
private fun EditButton(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = Palette.Fg) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = description, tint = tint) }
}

@Composable
private fun WidgetLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = Palette.TextDim,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        maxLines = 1,
    )
}

@Composable
private fun NumberWidget(r: ResolvedWidget, metric: Metric, value: Double?, big: Boolean) {
    val color = levelColor(value, r.warn, r.danger, Palette.Fg)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        WidgetLabel(r.label)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                metric.format(value),
                style = NumberStyle,
                color = color,
                fontSize = if (big) 54.sp else 40.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            Text(r.unit, color = Palette.TextDim, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun GaugeWidget(r: ResolvedWidget, metric: Metric, value: Double?, accent: Color) {
    val target = fraction(value, r.min, r.max)
    val animated by animateFloatAsState(target, animationSpec = tween(150), label = "gauge")
    val color = levelColor(value, r.warn, r.danger, accent)

    BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp)) {
        val numberSize = (minOf(maxWidth.value, maxHeight.value) * 0.24f).sp
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.085f
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f + diameter * 0.08f)
            val arcSize = Size(diameter, diameter)
            drawArc(Palette.Outline, 150f, 240f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (animated > 0.002f) {
                drawArc(color, 150f, 240f * animated, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                metric.format(value),
                style = NumberStyle,
                color = Palette.Fg,
                fontSize = numberSize,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            Text(r.unit, color = Palette.TextDim, fontSize = 14.sp)
        }
        WidgetLabel(r.label, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun BarWidget(r: ResolvedWidget, metric: Metric, value: Double?, accent: Color) {
    val target = fraction(value, r.min, r.max)
    val animated by animateFloatAsState(target, animationSpec = tween(150), label = "bar")
    val color = levelColor(value, r.warn, r.danger, accent)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            WidgetLabel(r.label, Modifier.weight(1f).padding(bottom = 4.dp))
            Text(metric.format(value), style = NumberStyle, color = Palette.Fg, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(4.dp))
            Text(r.unit, color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Palette.Outline),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(color),
            )
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
    val points = remember(history, vehicle, widget.metric) { history.mapNotNull { widget.metric.value(it, vehicle) } }
    val color = levelColor(value, r.warn, r.danger, accent)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            WidgetLabel(r.label, Modifier.weight(1f).padding(bottom = 3.dp))
            Text(widget.metric.format(value), style = NumberStyle, color = Palette.Fg, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(4.dp))
            Text(r.unit, color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
        Spacer(Modifier.height(8.dp))
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
                drawLine(Palette.Outline, Offset(0f, zy), Offset(size.width, zy), strokeWidth = 1.dp.toPx())
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
            drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
