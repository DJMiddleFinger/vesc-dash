package com.vescdash.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vescdash.data.niceStep
import com.vescdash.ui.theme.HeavyWideFont
import com.vescdash.ui.theme.Palette
import com.vescdash.ui.theme.WideFont
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Stadium value box, like the "58" next to a parameter in the Stark power-mode editor. */
@Composable
fun ValuePill(text: String, modifier: Modifier = Modifier, color: Color = Palette.Fg) {
    Text(
        text,
        modifier = modifier.clip(CircleShape).background(Palette.Surface2).padding(horizontal = 18.dp, vertical = 8.dp),
        color = color,
        fontFamily = HeavyWideFont,
        fontSize = 15.sp,
    )
}

/** Flat light strip over the bottom of the screen, like "Power Modes stored on the bike". */
@Composable
fun StarkToast(text: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = text != null,
        modifier = modifier,
        enter = fadeIn(tween(200)) + slideInVertically(tween(250)) { it / 2 },
        exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { it / 2 },
    ) {
        Text(
            text.orEmpty(),
            modifier = Modifier.background(Color(0xFFD0D0D0)).padding(horizontal = 16.dp, vertical = 10.dp),
            color = Color(0xFF121212),
            fontSize = 12.sp,
        )
    }
}

/**
 * The Stark mode-editor slider: a two-sided tick scale from 0, a glowing [color] track up to a
 * white thumb, and bright end labels. The thumb moves between [min] and [max] in multiples of
 * [step]; the scale runs on to the next round number past [max], like the gated zone in Stark's own.
 */
@Composable
fun StarkSlider(
    value: Float,
    onValue: (Float) -> Unit,
    min: Float,
    max: Float,
    color: Color,
    modifier: Modifier = Modifier,
    step: Float = 1f,
) {
    val measurer = rememberTextMeasurer()
    val current by rememberUpdatedState(onValue)
    val dim = Palette.TextDim
    val bright = Palette.Fg
    val trackOff = Color(0xFF1B1B1B)
    val tickDim = Color(0xFF555555)
    val labelStyle = TextStyle(fontFamily = WideFont, fontSize = 9.sp)
    val major = niceStep((max / 5f).toDouble()).toFloat()
    val top = ceil(max / major - 1e-4f) * major

    Canvas(
        modifier
            .fillMaxWidth()
            .height(84.dp)
            .pointerInput(min, max, step) {
                detectTapGestures { current(valueAt(it.x, size.width.toFloat(), 16.dp.toPx(), top, min, max, step)) }
            }
            .pointerInput(min, max, step) {
                detectHorizontalDragGestures { change, _ ->
                    current(valueAt(change.position.x, size.width.toFloat(), 16.dp.toPx(), top, min, max, step))
                }
            },
    ) {
        val pad = 16.dp.toPx()
        val w = size.width - 2 * pad
        val cy = size.height / 2f
        fun x(v: Float) = pad + v / top * w
        val thumbX = x(value.coerceIn(min, max))

        // Ticks: tall ones at every labelled value, short ones halfway between
        var i = 0
        while (i * major <= top + 1e-4f) {
            val v = i * major
            val end = i == 0 || v >= top - 1e-4f
            val tall = if (end) 22.dp.toPx() else 16.dp.toPx()
            drawLine(if (end) bright else tickDim, Offset(x(v), cy - tall / 2f), Offset(x(v), cy + tall / 2f), strokeWidth = if (end) 1.5.dp.toPx() else 1.dp.toPx())
            if (v + major / 2f < top - 1e-4f) {
                drawLine(tickDim, Offset(x(v + major / 2f), cy - 4.dp.toPx()), Offset(x(v + major / 2f), cy + 4.dp.toPx()), strokeWidth = 1.dp.toPx())
            }
            val text = if (major < 1f) "%.1f".format(v) else v.roundToInt().toString()
            val layout = measurer.measure(text, labelStyle.copy(color = if (end) bright else dim))
            val lx = (x(v) - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(lx, cy - 18.dp.toPx() - layout.size.height))
            drawText(layout, topLeft = Offset(lx, cy + 18.dp.toPx()))
            i++
        }

        // Track with its glow
        drawLine(trackOff, Offset(pad, cy), Offset(pad + w, cy), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color.copy(alpha = 0.14f), Offset(pad, cy), Offset(thumbX, cy), strokeWidth = 14.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color.copy(alpha = 0.28f), Offset(pad, cy), Offset(thumbX, cy), strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, Offset(pad, cy), Offset(thumbX, cy), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Color.White, 13.dp.toPx(), Offset(thumbX, cy))
    }
}

private fun valueAt(x: Float, width: Float, pad: Float, top: Float, min: Float, max: Float, step: Float): Float {
    val v = ((x - pad) / (width - 2 * pad)).coerceIn(0f, 1f) * top
    return ((v / step).roundToInt() * step).coerceIn(min, max)
}

/** A well for [StarkSlider]: slightly lighter than the card it sits in. */
@Composable
fun StarkWell(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().clip(Palette.innerShape).background(Palette.Surface2), contentAlignment = Alignment.Center) { content() }
}

/** Stark's active-tab marker: a short red bar along the bottom edge. */
@Composable
fun Modifier.starkUnderline(active: Boolean): Modifier {
    val red = Palette.Red
    return drawBehind {
        if (active) {
            val w = size.width * 0.6f
            drawRect(red, Offset((size.width - w) / 2f, size.height - 3.dp.toPx()), Size(w, 3.dp.toPx()))
        }
    }
}

/**
 * Stark's pairing ring: a thin gray circle with the Bluetooth glyph, a red arc sweeping round it
 * while [busy], and solid green once [connected].
 */
@Composable
fun StarkRing(connected: Boolean, busy: Boolean, modifier: Modifier = Modifier) {
    val angle = if (busy) {
        rememberInfiniteTransition(label = "ring").animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "ringAngle").value
    } else {
        0f
    }
    val color = if (connected) Palette.Good else Palette.Red
    val track = Palette.TextDim.copy(alpha = 0.5f)
    Box(modifier.size(88.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val stroke = 2.dp.toPx()
            drawCircle(if (connected) color else track, style = Stroke(stroke))
            if (busy) {
                drawArc(color.copy(alpha = 0.25f), angle, 90f, false, style = Stroke(stroke * 3f, cap = StrokeCap.Round))
                drawArc(color, angle, 90f, false, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = if (connected || busy) color else Palette.TextDim, modifier = Modifier.size(32.dp))
    }
}
