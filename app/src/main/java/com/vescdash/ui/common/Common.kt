package com.vescdash.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        text,
        modifier = modifier,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
    )
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
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
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
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
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
