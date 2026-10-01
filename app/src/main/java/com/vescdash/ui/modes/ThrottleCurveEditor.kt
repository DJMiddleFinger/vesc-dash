package com.vescdash.ui.modes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vescdash.data.VehicleMath
import com.vescdash.ui.theme.Palette
import kotlin.math.roundToInt

/** How far either way the curve can be bent; the same range the exponent slider had. */
private const val MAX_EXP = 2.0

/**
 * A mode's throttle response as a graph you can edit: throttle position across, power out going up.
 * Drag or tap anywhere to bend the curve (the handle sits at half throttle); above the dashed straight
 * line you get power sooner, below it later. [exp] null means your Setup base value, shown dimmed.
 */
@Composable
internal fun ThrottleCurveEditor(
    exp: Double?,
    base: Double,
    color: Color,
    onChange: (Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = (exp ?: base).coerceIn(-MAX_EXP, MAX_EXP)
    val change by rememberUpdatedState(onChange)
    val measurer = rememberTextMeasurer()
    val outline = Palette.Outline
    val dim = Palette.TextDim
    val labelStyle = TextStyle(color = dim, fontSize = 10.sp)

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) { detectTapGestures { change(expAt(it.y, size.height.toFloat(), PAD_DP.dp.toPx())) } }
                .pointerInput(Unit) {
                    detectDragGestures { c, _ -> change(expAt(c.position.y, size.height.toFloat(), PAD_DP.dp.toPx())) }
                },
        ) {
            val pad = PAD_DP.dp.toPx()
            val w = size.width - 2 * pad
            val h = size.height - 2 * pad
            fun px(x: Double) = pad + (x * w).toFloat()
            fun py(y: Double) = pad + ((1 - y) * h).toFloat()
            val a = if (exp == null) 0.5f else 1f

            for (q in 0..4) {
                val g = q / 4.0
                drawLine(outline, Offset(px(0.0), py(g)), Offset(px(1.0), py(g)), strokeWidth = 1f)
                drawLine(outline, Offset(px(g), py(0.0)), Offset(px(g), py(1.0)), strokeWidth = 1f)
            }
            drawLine(
                dim.copy(alpha = 0.55f), Offset(px(0.0), py(0.0)), Offset(px(1.0), py(1.0)),
                strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )

            val line = Path().apply {
                for (i in 0..40) {
                    val x = i / 40.0
                    val y = VehicleMath.throttleCurve(x, shown)
                    if (i == 0) moveTo(px(x), py(y)) else lineTo(px(x), py(y))
                }
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(px(1.0), py(0.0))
                lineTo(px(0.0), py(0.0))
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.4f * a), color.copy(alpha = 0.02f)), startY = pad, endY = pad + h))
            drawPath(line, color.copy(alpha = a), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            val handle = Offset(px(0.5), py(VehicleMath.throttleCurve(0.5, shown)))
            drawCircle(Color.White, 11.dp.toPx(), handle)
            drawCircle(color.copy(alpha = a), 11.dp.toPx(), handle, style = Stroke(3.dp.toPx()))

            drawText(measurer.measure("power", labelStyle), topLeft = Offset(px(0.0) + 4.dp.toPx(), py(1.0)))
            val t = measurer.measure("throttle", labelStyle)
            drawText(t, topLeft = Offset(px(1.0) - t.size.width - 4.dp.toPx(), py(0.0) - t.size.height - 2.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "%s · %.1f".format(
                    when {
                        shown < -0.05 -> "Soft start"
                        shown > 0.05 -> "Punchy"
                        else -> "Linear"
                    },
                    shown,
                ),
                color = Palette.Fg,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            if (exp != null) TextButton(onClick = { change(null) }) { Text("USE BASE") }
        }
    }
}

private const val PAD_DP = 12

/** The exponent for a touch at [y] (px from the top) in a [height]-px graph with [pad] px around the plot. */
private fun expAt(y: Float, height: Float, pad: Float): Double {
    val mid = 1.0 - ((y - pad) / (height - 2 * pad)).coerceIn(0f, 1f)
    return ((VehicleMath.throttleExpForMid(mid).coerceIn(-MAX_EXP, MAX_EXP)) * 10).roundToInt() / 10.0
}
