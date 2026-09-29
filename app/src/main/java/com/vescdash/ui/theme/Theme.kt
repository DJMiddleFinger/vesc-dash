package com.vescdash.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

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

/** Tabular figures so numbers don't jitter as they change. */
val NumberStyle = TextStyle(fontFeatureSettings = "tnum")

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
