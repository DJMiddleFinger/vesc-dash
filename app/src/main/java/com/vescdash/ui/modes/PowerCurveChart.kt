package com.vescdash.ui.modes

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vescdash.data.CurvePoint
import com.vescdash.data.DriveMode
import com.vescdash.data.KMH_TO_MPH
import com.vescdash.data.VehicleMath
import com.vescdash.data.VehicleSettings
import com.vescdash.data.niceStep
import com.vescdash.data.speedUnit
import com.vescdash.ui.theme.Palette
import kotlin.math.roundToInt

/**
 * Estimated power-vs-speed curve for a mode, drawn over a dashed curve for the
 * full (100%) base limits so modes can be compared at a glance.
 */
@Composable
fun PowerCurveChart(
    mode: DriveMode,
    vehicle: VehicleSettings,
    modifier: Modifier = Modifier,
    detailed: Boolean = true,
) {
    val color = Color(mode.color)
    val curve = remember(mode, vehicle) { VehicleMath.powerCurve(mode, vehicle) }
    val fullCurve = remember(vehicle) { VehicleMath.powerCurve(null, vehicle) }
    val topSpeed = remember(vehicle) { VehicleMath.maxSpeedKmh(vehicle).coerceAtLeast(1.0) }
    val xMax = topSpeed * 1.05
    val yMax = remember(fullCurve) { fullCurve.maxOf { it.kw }.coerceAtLeast(0.1) * 1.12 }
    val speedFactor = if (vehicle.imperial) KMH_TO_MPH else 1.0
    val unit = speedUnit(vehicle)
    val measurer = rememberTextMeasurer()
    val outline = Palette.Outline
    val dim = Palette.TextDim
    val labelStyle = TextStyle(color = dim, fontSize = 10.sp, fontFeatureSettings = "tnum")

    Canvas(modifier) {
        val left = if (detailed) 30.dp.toPx() else 0f
        val bottom = if (detailed) 18.dp.toPx() else 0f
        val top = if (detailed) 14.dp.toPx() else 2.dp.toPx()
        val w = size.width - left
        val h = size.height - bottom - top
        fun px(kmh: Double) = left + (kmh / xMax * w).toFloat()
        fun py(kw: Double) = top + (h - kw / yMax * h).toFloat()

        if (detailed) {
            val step = niceStep(yMax / 4)
            var k = 0.0
            while (k <= yMax + 1e-9) {
                val y = py(k)
                drawLine(outline, Offset(left, y), Offset(size.width, y), strokeWidth = 1f)
                val text = if (step < 1) String.format(java.util.Locale.US, "%.1f", k) else k.roundToInt().toString()
                val layout = measurer.measure(text, labelStyle)
                drawText(layout, topLeft = Offset(left - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f))
                k += step
            }
            val kwLabel = measurer.measure("kW", labelStyle)
            drawText(kwLabel, topLeft = Offset(0f, 0f))

            listOf(0.0, topSpeed / 2, topSpeed).forEach { s ->
                val layout = measurer.measure((s * speedFactor).roundToInt().toString(), labelStyle)
                val x = (px(s) - layout.size.width / 2f).coerceIn(left, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(x, top + h + 3.dp.toPx()))
            }
            val unitLabel = measurer.measure(unit, labelStyle)
            drawText(unitLabel, topLeft = Offset(size.width - unitLabel.size.width, 0f))
        }

        fun pathOf(points: List<CurvePoint>) = Path().apply {
            points.forEachIndexed { i, p ->
                if (i == 0) moveTo(px(p.kmh), py(p.kw)) else lineTo(px(p.kmh), py(p.kw))
            }
        }

        drawPath(
            pathOf(fullCurve),
            dim.copy(alpha = 0.55f),
            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
        )
        val line = pathOf(curve)
        val fill = Path().apply {
            addPath(line)
            lineTo(px(curve.first().kmh), py(0.0))
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.45f), color.copy(alpha = 0.03f)), startY = top, endY = top + h))
        drawPath(line, color, style = Stroke((if (detailed) 2.5 else 2.0).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
