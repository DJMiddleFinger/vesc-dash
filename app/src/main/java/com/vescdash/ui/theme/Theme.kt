package com.vescdash.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vescdash.R
import com.vescdash.data.AppAppearance

/**
 * Everything that differs between the two appearances: colours, corner radii and type faces.
 * [Classic] is the original look. Read it through [Palette] so call sites stay short.
 */
@Immutable
class AppStyle(
    val stark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val outline: Color,
    val fg: Color,
    val textDim: Color,
    val warn: Color,
    val danger: Color,
    val good: Color,
    val defaultAccent: Color,
    /** Dialog background. */
    val dialog: Color,
    /** Top bar and side rail (Stark). */
    val chrome: Color,
    /** Power/torque channel colour. */
    val cyan: Color,
    /** Regen/brake channel colour and the active-tab marker. */
    val red: Color,
    val cardRadius: Dp,
    val innerRadius: Dp,
    /** Face for titles, captions and big numbers; null = the default sans. */
    val display: FontFamily?,
) {
    companion object {
        val Classic = AppStyle(
            stark = false,
            bg = Color(0xFF08080A), surface = Color(0xFF131316), surface2 = Color(0xFF1C1C21), outline = Color(0xFF2B2B32),
            fg = Color(0xFFF2F2F5), textDim = Color(0xFF8B8B96),
            warn = Color(0xFFFFB020), danger = Color(0xFFFF3B30), good = Color(0xFF30D158), defaultAccent = Color(0xFFFF6A00),
            dialog = Color(0xFF1C1C21), chrome = Color(0xFF131316),
            cyan = Color(0xFF29B6F6), red = Color(0xFFFF3B30),
            cardRadius = 20.dp, innerRadius = 14.dp, display = null,
        )

        /** Neutral grays with no blue tint and tonal layering, in the spirit of the Stark Varg phone app. */
        val Stark = AppStyle(
            stark = true,
            bg = Color(0xFF121212), surface = Color(0xFF202020), surface2 = Color(0xFF2C2C2C), outline = Color(0xFF2E2E2E),
            fg = Color(0xFFF8F8F8), textDim = Color(0xFF888888),
            warn = Color(0xFFF88018), danger = Color(0xFFF81818), good = Color(0xFF48F8A0), defaultAccent = Color(0xFF4BF9EF),
            dialog = Color(0xFF202020), chrome = Color(0xFF000000),
            cyan = Color(0xFF4BF9EF), red = Color(0xFFFF1518),
            cardRadius = 2.dp, innerRadius = 2.dp, display = WideFont,
        )
    }
}

val LocalAppStyle = staticCompositionLocalOf { AppStyle.Classic }

/** The active appearance's colours. Classic's values are the originals; Stark's swap in through [LocalAppStyle]. */
object Palette {
    val Bg: Color @Composable @ReadOnlyComposable get() = LocalAppStyle.current.bg
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalAppStyle.current.surface
    val Surface2: Color @Composable @ReadOnlyComposable get() = LocalAppStyle.current.surface2
    val Outline: Color @Composable @ReadOnlyComposable get() = LocalAppStyle.current.outline
    val Fg: Color @Composable @ReadOnlyComposable get() = LocalAppStyle.current.fg
    val TextDim: Color @Composable @ReadOnlyComposable get() = LocalAppStyle.current.textDim
    val Warn: Color @Composable @ReadOnlyComposable get() = LocalAppStyle.current.warn
    val Danger: Color @Composable @ReadOnlyComposable get() = LocalAppStyle.current.danger
    val Good: Color @Composable @ReadOnlyComposable get() = LocalAppStyle.current.good
    val DefaultAccent: Color @Composable @ReadOnlyComposable get() = LocalAppStyle.current.defaultAccent
    val Dialog: Color @Composable @ReadOnlyComposable get() = LocalAppStyle.current.dialog
    val Chrome: Color @Composable @ReadOnlyComposable get() = LocalAppStyle.current.chrome
    val Cyan: Color @Composable @ReadOnlyComposable get() = LocalAppStyle.current.cyan
    val Red: Color @Composable @ReadOnlyComposable get() = LocalAppStyle.current.red
    val stark: Boolean @Composable @ReadOnlyComposable get() = LocalAppStyle.current.stark
    val cardShape: RoundedCornerShape @Composable @ReadOnlyComposable get() = RoundedCornerShape(LocalAppStyle.current.cardRadius)
    val innerShape: RoundedCornerShape @Composable @ReadOnlyComposable get() = RoundedCornerShape(LocalAppStyle.current.innerRadius)
    val display: FontFamily? @Composable @ReadOnlyComposable get() = LocalAppStyle.current.display
}

/** Wide geometric face used by the landscape ride screen (Michroma, SIL OFL). */
val WideFont = FontFamily(Font(R.font.michroma))

/** Heavier wide face for the big Stark digits (Krona One, SIL OFL). */
val HeavyWideFont = FontFamily(Font(R.font.krona_one))

/** Light geometric sans for Stark body text (Outfit, SIL OFL; a variable font, so each weight is pinned). */
@OptIn(ExperimentalTextApi::class)
val BodyFont = FontFamily(
    listOf(
        FontWeight.Light to 300, FontWeight.Normal to 400, FontWeight.Medium to 500,
        FontWeight.SemiBold to 600, FontWeight.Bold to 700, FontWeight.Black to 900,
    ).map { (w, v) -> Font(R.font.outfit, w, variationSettings = FontVariation.Settings(FontVariation.weight(v))) },
)

/** Tabular figures so numbers don't jitter as they change. */
val NumberStyle = TextStyle(fontFeatureSettings = "tnum")

/**
 * What dashboard widgets draw with. The Dash tab uses [dash], the app palette at fixed sizes;
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
    /** Gauges, bars and graph lines carry a soft halo in their own colour. */
    val glow: Boolean = false,
    /** Dash-tab size of the caption above a widget, in sp. */
    val captionSp: Float = 11f,
    /** The wide digits run bigger than the default ones, so the Dash tab shrinks them a little. */
    val digitScale: Float = 1f,
) {
    /** [dash] on the Dash tab; on the ride screen [frac] of [ref] (a length from the widget), kept within [min]..[max]. */
    fun scale(dash: Float, ref: Float, frac: Float, min: Float = 0f, max: Float = Float.MAX_VALUE): Float =
        if (scaleToBox) (ref * frac).coerceIn(min, max) else dash

    /** Label size: 11 sp on the Dash tab; on the ride screen [frac] of [ref], but always a small caption. */
    fun labelSize(ref: Float, frac: Float): Float = scale(captionSp, ref, frac, min = 9f, max = 13f)

    companion object {
        fun dash(s: AppStyle) = if (s.stark) {
            WidgetTheme(
                s.surface, s.fg, s.textDim, s.surface2, s.warn, s.danger,
                // The heavy face has one weight, so don't let the widgets' Black weight fake a bolder one.
                NumberStyle.copy(fontFamily = HeavyWideFont, fontSynthesis = FontSynthesis.None),
                TextStyle(fontFamily = WideFont, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None, letterSpacing = 0.1.em),
                scaleToBox = false, glow = true, captionSp = 9f, digitScale = 0.8f,
            )
        } else {
            WidgetTheme(
                s.surface, s.fg, s.textDim, s.outline, s.warn, s.danger,
                NumberStyle, TextStyle(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp), scaleToBox = false,
            )
        }
    }
}

val LocalWidgetTheme = staticCompositionLocalOf { WidgetTheme.dash(AppStyle.Classic) }

@Composable
fun VescDashTheme(accent: Color, appearance: AppAppearance, content: @Composable () -> Unit) {
    val style = if (appearance == AppAppearance.STARK) AppStyle.Stark else AppStyle.Classic
    val widgetTheme = remember(style) { WidgetTheme.dash(style) }
    CompositionLocalProvider(LocalAppStyle provides style, LocalWidgetTheme provides widgetTheme) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                // Stark keeps the power-channel cyan as its accent whatever the active mode's colour is.
                primary = if (style.stark) style.cyan else accent,
                onPrimary = Color.Black,
                secondary = if (style.stark) style.cyan else accent,
                onSecondary = Color.Black,
                background = style.bg,
                onBackground = style.fg,
                surface = style.surface,
                onSurface = style.fg,
                surfaceVariant = style.surface2,
                onSurfaceVariant = style.textDim,
                surfaceContainerLow = style.surface,
                surfaceContainer = style.surface,
                surfaceContainerHigh = style.surface2,
                outline = style.outline,
                outlineVariant = style.outline,
                error = style.danger,
            ),
            typography = if (style.stark) starkTypography() else Typography(),
            shapes = if (style.stark) starkShapes() else Shapes(),
            content = content,
        )
    }
}

/** Light Outfit for body text; buttons and titles use the wide caps face. */
private fun starkTypography(): Typography {
    val base = Typography()
    fun TextStyle.body(weight: FontWeight = FontWeight.Light) = copy(fontFamily = BodyFont, fontWeight = weight)
    fun caps(size: Float) = TextStyle(fontFamily = WideFont, fontSize = size.sp, letterSpacing = 0.1.em, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None)
    return base.copy(
        displayLarge = base.displayLarge.body(), displayMedium = base.displayMedium.body(), displaySmall = base.displaySmall.body(),
        headlineLarge = base.headlineLarge.body(), headlineMedium = base.headlineMedium.body(), headlineSmall = caps(15f),
        titleLarge = base.titleLarge.body(), titleMedium = base.titleMedium.body(FontWeight.Normal), titleSmall = base.titleSmall.body(FontWeight.Normal),
        bodyLarge = base.bodyLarge.body(), bodyMedium = base.bodyMedium.body(), bodySmall = base.bodySmall.body(),
        labelLarge = caps(11f), labelMedium = caps(10f), labelSmall = caps(9f),
    )
}

/** Near-square corners, with stadium buttons (Material's own default for those). */
private fun starkShapes() = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(4.dp),
)
