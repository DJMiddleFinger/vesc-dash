package com.vescdash.ui.ride

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vescdash.data.RideLayout
import com.vescdash.data.RideRect
import com.vescdash.ui.MainViewModel
import com.vescdash.ui.dash.WidgetBody
import com.vescdash.ui.theme.LocalWidgetTheme
import com.vescdash.ui.theme.NumberStyle
import com.vescdash.ui.theme.WideFont
import com.vescdash.ui.theme.WidgetTheme
import kotlin.math.min

/** Corner radius of a widget's card, as a fraction of its shorter side. */
internal const val CARD_CORNER = 0.16f

/**
 * Your own layout: dashboard widgets, the mode pill and the warning icons placed freely on the
 * screen, with each widget's text sized to its box. Edited with [RideLayoutEditor].
 */
@Composable
internal fun CustomRide(vm: MainViewModel, d: RideData, c: RidePalette, onCycleMode: () -> Unit, onHome: () -> Unit) {
    val layout by vm.rideLayout.collectAsStateWithLifecycle()
    val intro = rememberIntroProgress()

    // The layout is stored as screen positions, so it keeps left-to-right even where the app is right-to-left.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()

            RideLayoutView(vm, layout, d, c, Modifier.fillMaxSize(), intro)

            TapTarget(layout.modePill.toRect(w, h), enabled = true, onClick = onCycleMode)
            // Centre-right, like the other styles, and only while stopped.
            HomeButton(d.stopped, Offset(0.92f * w, 0.6f * h), 0.17f * h, c, onHome)
            RideRoundButton(
                d.stopped, Offset(0.92f * w - 0.22f * h, 0.6f * h), 0.17f * h, c,
                Icons.Filled.Edit, "Edit layout", vm::openLayoutEditor,
            )
        }
    }
}

/**
 * [layout] drawn over the whole box: widgets first to last, then the mode pill and warnings on
 * top. Shared by the ride screen and the editor, which passes [samples] so the warnings can be
 * placed even when none are active. [intro] is the start-up progress (1 = settled).
 */
@Composable
internal fun RideLayoutView(
    vm: MainViewModel,
    layout: RideLayout,
    d: RideData,
    c: RidePalette,
    modifier: Modifier = Modifier,
    intro: Float = 1f,
    samples: Boolean = false,
) {
    val telemetry by vm.telemetry.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val vehicle by vm.vehicle.collectAsStateWithLifecycle()
    val theme = remember(c) { c.widgetTheme() }
    val measurer = rememberTextMeasurer()
    val icons = rememberRideIcons()
    val pulse = rememberWarningPulse()
    val fadeIn = introPhase(intro, 0f, 0.4f)
    val sampleWarnings = samples && d.warnings.isEmpty()

    BoxWithConstraints(modifier.background(c.bg)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val width = maxWidth
        val height = maxHeight

        CompositionLocalProvider(LocalWidgetTheme provides theme, LocalLayoutDirection provides LayoutDirection.Ltr) {
            // A plain Box of their own, so that reordering the widgets reorders how they stack.
            Box(Modifier.fillMaxSize()) {
                layout.items.forEachIndexed { i, item ->
                    key(item.widget.id) {
                        // Entry: widgets fade and scale in one after another.
                        val start = (0.05f * i).coerceAtMost(0.4f)
                        val itemIn = introPhase(intro, start, start + 0.4f)
                        Box(
                            Modifier
                                .place(item.rect, width, height)
                                .graphicsLayer {
                                    alpha = itemIn
                                    scaleX = 0.92f + 0.08f * itemIn
                                    scaleY = 0.92f + 0.08f * itemIn
                                }
                                .clip(RoundedCornerShape(minOf(width * item.rect.w, height * item.rect.h) * CARD_CORNER))
                                .background(theme.surface),
                        ) {
                            WidgetBody(item.widget, telemetry, history, vehicle.historySamples, vehicle)
                        }
                    }
                }
            }
        }

        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = fadeIn }) {
            drawModePill(measurer, icons, c, d, layout.modePill.toRect(w, h))
        }
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = if (sampleWarnings) 0.5f * fadeIn else fadeIn }) {
            val area = layout.warnings.toRect(w, h)
            val size = drawWarnings(measurer, icons, c, if (sampleWarnings) SampleWarnings else d.warnings, area, pulse)
            if (sampleWarnings) {
                val label = Offset(area.right - 0.15f * size, area.bottom - 0.2f * size)
                drawCentered(measurer, AnnotatedString("WARNINGS"), wide(0.22f * size, c.label, 0.08f), label, alignX = 1f)
            }
        }
    }
}

private val SampleWarnings = listOf(RideWarning.NoLink, RideWarning.ControllerHot(75.0, 60.0))

/**
 * Warning icons stacked along the long side of [area], sized so about three fit and never
 * bigger than its short side. Returns the icon size.
 */
private fun DrawScope.drawWarnings(
    m: TextMeasurer,
    icons: RideIcons,
    c: RidePalette,
    warnings: List<RideWarning>,
    area: Rect,
    pulse: Float,
): Float {
    val horizontal = area.width > area.height
    val size = if (horizontal) min(area.height, area.width / 7.5f) else min(area.width / 3.2f, area.height / 4f)
    val y = if (horizontal) area.center.y else area.top + size / 2f
    drawWarningStack(m, icons, c, warnings, area.left + size / 2f, y, size, pulse, horizontal)
    return size
}

/** Places a child at [r] of the [w] × [h] box it's in. */
private fun Modifier.place(r: RideRect, w: Dp, h: Dp) = offset(w * r.x, h * r.y).size(w * r.w, h * r.h)

internal fun RideRect.toRect(w: Float, h: Float) = Rect(x * w, y * h, right * w, bottom * h)

/** Widgets on the ride screen use the ride palette and the wide face, and size text from their box. */
private fun RidePalette.widgetTheme() = WidgetTheme(
    surface = tile,
    fg = text,
    dim = label,
    outline = track,
    warn = amber,
    danger = red,
    // Michroma has one weight, so don't let the widgets' Black weight fake a bold.
    number = NumberStyle.copy(fontFamily = WideFont, fontSynthesis = FontSynthesis.None),
    label = TextStyle(fontFamily = WideFont, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None, letterSpacing = 0.05.em),
    scaleToBox = true,
)
