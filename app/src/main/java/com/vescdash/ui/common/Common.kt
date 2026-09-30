package com.vescdash.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vescdash.ui.theme.HeavyWideFont
import com.vescdash.ui.theme.Palette
import kotlin.math.floor

fun Double?.toFieldText(): String = when {
    this == null -> ""
    this == floor(this) && kotlin.math.abs(this) < 1e12 -> this.toLong().toString()
    else -> this.toString()
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Palette.TextDim) {
    Text(
        // The wide caps face draws % badly ("o/o"), so Stark borrows the heavy face for it.
        if (Palette.stark && '%' in text) buildAnnotatedString {
            text.forEach { if (it == '%') withStyle(SpanStyle(fontFamily = HeavyWideFont)) { append(it) } else append(it) }
        } else AnnotatedString(text),
        modifier = modifier,
        color = color,
        fontSize = if (Palette.stark) 10.sp else 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = Palette.display,
        letterSpacing = if (Palette.stark) 1.8.sp else 1.5.sp,
    )
}

/** Upper-case in Stark, where labels and buttons are set in the wide caps face. */
@Composable
fun String.caps(): String = if (Palette.stark) uppercase() else this

@Composable
fun Card(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Palette.cardShape)
            .background(Palette.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null) SectionLabel(title)
        content()
    }
}

@Composable
fun Banner(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(if (Palette.stark) Palette.innerShape else RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 14.dp, vertical = if (Palette.stark) 6.dp else 10.dp),
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/**
 * Numeric text field that only reports values that parse and fall inside [range];
 * the text itself stays editable so partially-typed numbers aren't clobbered.
 */
@Composable
fun SettingNumber(
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    help: String? = null,
    integer: Boolean = false,
    onValue: (Double) -> Unit,
) {
    fun ok(d: Double?) = d != null && d in range && (!integer || d == floor(d))
    var text by remember { mutableStateOf(value.toFieldText()) }
    LaunchedEffect(value) {
        if (text.toDoubleOrNull() != value) text = value.toFieldText()
    }
    val valid = ok(text.toDoubleOrNull())
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t
            val d = t.toDoubleOrNull()
            if (d != null && ok(d)) onValue(d)
        },
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        isError = !valid,
        colors = appTextFieldColors(),
        supportingText = when {
            !valid -> { { Text("Enter ${range.start.toFieldText()} – ${range.endInclusive.toFieldText()}") } }
            help != null -> { { Text(help) } }
            else -> null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
fun SettingSwitch(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Palette.Fg, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = Palette.TextDim, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = appSwitchColors())
    }
}

/** Stark's fields are filled dark wells with a faint border that lights up when focused. */
@Composable
fun appTextFieldColors(): TextFieldColors =
    if (Palette.stark) {
        OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Palette.Surface2,
            unfocusedContainerColor = Palette.Surface2,
            focusedBorderColor = Palette.Fg,
            unfocusedBorderColor = Palette.TextDim.copy(alpha = 0.4f),
            focusedLabelColor = Palette.Fg,
            unfocusedLabelColor = Palette.TextDim,
            cursorColor = Palette.Fg,
        )
    } else {
        OutlinedTextFieldDefaults.colors()
    }

/** Stark toggles: a green track with a white thumb when on, a dark well with a gray thumb when off. */
@Composable
fun appSwitchColors(on: Color = Palette.Good): SwitchColors =
    if (Palette.stark) {
        SwitchDefaults.colors(
            checkedTrackColor = on,
            checkedThumbColor = Color.White,
            checkedBorderColor = on,
            uncheckedTrackColor = Palette.Surface2,
            uncheckedThumbColor = Palette.TextDim,
            uncheckedBorderColor = Palette.TextDim.copy(alpha = 0.4f),
        )
    } else {
        SwitchDefaults.colors()
    }

/** Filter chip; Stark's are stadium pills with a caps caption and a light outline when selected. */
@Composable
fun AppChip(selected: Boolean, label: String, onClick: () -> Unit) {
    if (!Palette.stark) {
        FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
        return
    }
    Text(
        label.uppercase(),
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Palette.Surface2 else Color(0xFF151515))
            .border(1.dp, if (selected) Palette.Fg.copy(alpha = 0.85f) else Color(0xFF3A3A3A), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = if (selected) Palette.Fg else Palette.TextDim,
        fontFamily = Palette.display,
        fontSize = 10.sp,
        letterSpacing = 1.sp,
    )
}

/**
 * Text button with an optional leading [icon]. [primary] is filled in Classic; Stark draws every
 * button as an outlined stadium with a caps caption, so [primary] only changes its border.
 */
@Composable
fun AppButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    color: Color = Color.Unspecified,
) {
    val content: @Composable () -> Unit = {
        if (icon != null) Icon(icon, contentDescription = null)
        Text(if (icon != null) " ${label.caps()}" else label.caps(), color = color)
    }
    when {
        Palette.stark -> OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            border = BorderStroke(1.dp, (if (primary) Palette.Fg else Palette.TextDim).copy(alpha = if (enabled) 0.9f else 0.3f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Fg),
        ) { content() }
        primary -> Button(onClick = onClick, modifier = modifier, enabled = enabled) { content() }
        else -> OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) { content() }
    }
}
