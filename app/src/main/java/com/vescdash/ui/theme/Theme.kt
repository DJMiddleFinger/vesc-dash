package com.vescdash.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vescdash.R

object Palette {
    val Bg = Color(0xFF08080A)
    val Surface = Color(0xFF131316)
    val Surface2 = Color(0xFF1C1C21)
    val Outline = Color(0xFF2B2B32)
    val Fg = Color(0xFFF2F2F5)
    val TextDim = Color(0xFF8B8B96)
    val Warn = Color(0xFFFFB020)
    val Danger = Color(0xFFFF3B30)
    val Good = Color(0xFF30D158)
    val DefaultAccent = Color(0xFFFF6A00)
}

/** Wide geometric face used by the landscape ride screen (Michroma, SIL OFL). */
val WideFont = FontFamily(Font(R.font.michroma))

/** Tabular figures so numbers don't jitter as they change. */
val NumberStyle = TextStyle(fontFeatureSettings = "tnum")

/**
 * What dashboard widgets draw with. The Dash tab uses [Dash], the app palette at fixed sizes;
 * the ride screen provides its own colours and face, and sizes text from each widget's box.
 */
class WidgetTheme(
    val surface: Color,
    val fg: Color,
    val dim: Color,
    val outline: Color,
    val warn: Color,
    val danger: Color,
    val number: TextStyle,
    val label: TextStyle,
    /** Text and padding grow with the widget's box instead of using the Dash tab's fixed sizes. */
    val scaleToBox: Boolean,
) {
    /** [dash] on the Dash tab; on the ride screen [frac] of [ref] (a length from the widget), kept within [min]..[max]. */
    fun scale(dash: Float, ref: Float, frac: Float, min: Float = 0f, max: Float = Float.MAX_VALUE): Float =
        if (scaleToBox) (ref * frac).coerceIn(min, max) else dash

    /** Label size: 11 sp on the Dash tab; on the ride screen [frac] of [ref], but always a small caption. */
    fun labelSize(ref: Float, frac: Float): Float = scale(11f, ref, frac, min = 9f, max = 13f)

    companion object {
        val Dash = WidgetTheme(
            Palette.Surface, Palette.Fg, Palette.TextDim, Palette.Outline, Palette.Warn, Palette.Danger,
            NumberStyle, TextStyle(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp), scaleToBox = false,
        )
    }
}

val LocalWidgetTheme = staticCompositionLocalOf { WidgetTheme.Dash }

@Composable
fun VescDashTheme(accent: Color, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
            onPrimary = Color.Black,
            secondary = accent,
            onSecondary = Color.Black,
            background = Palette.Bg,
            onBackground = Palette.Fg,
            surface = Palette.Surface,
            onSurface = Palette.Fg,
            surfaceVariant = Palette.Surface2,
            onSurfaceVariant = Palette.TextDim,
            surfaceContainerLow = Palette.Surface,
            surfaceContainer = Palette.Surface,
            surfaceContainerHigh = Palette.Surface2,
            outline = Palette.Outline,
            outlineVariant = Palette.Outline,
            error = Palette.Danger,
        ),
        typography = Typography(),
        content = content,
    )
}
