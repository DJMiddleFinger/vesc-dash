package com.vescdash.ui.dash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vescdash.data.DashWidget
import com.vescdash.data.Metric
import com.vescdash.data.VehicleSettings
import com.vescdash.data.WidgetSize
import com.vescdash.data.WidgetType
import com.vescdash.data.defaultRange
import com.vescdash.data.defaultThresholds
import com.vescdash.data.unit
import com.vescdash.ui.common.AppButton
import com.vescdash.ui.common.AppChip
import com.vescdash.ui.common.SectionLabel
import com.vescdash.ui.common.appTextFieldColors
import com.vescdash.ui.common.caps
import com.vescdash.ui.common.toFieldText
import com.vescdash.ui.theme.Palette

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WidgetEditorSheet(
    initial: DashWidget?,
    vehicle: VehicleSettings,
    onDismiss: () -> Unit,
    onSave: (DashWidget) -> Unit,
    /** Half/full width only means something on the Dash tab's grid. */
    showSize: Boolean = true,
) {
    val base = remember {
        initial ?: Metric.SPEED.defaultThresholds().let { th ->
            DashWidget(type = WidgetType.NUMBER, metric = Metric.SPEED, warn = th?.first, danger = th?.second)
        }
    }
    var type by remember { mutableStateOf(base.type) }
    var metric by remember { mutableStateOf(base.metric) }
    var size by remember { mutableStateOf(base.size) }
    var label by remember { mutableStateOf(base.label ?: "") }
    var minText by remember { mutableStateOf(base.min.toFieldText()) }
    var maxText by remember { mutableStateOf(base.max.toFieldText()) }
    var warnText by remember { mutableStateOf(base.warn.toFieldText()) }
    var dangerText by remember { mutableStateOf(base.danger.toFieldText()) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val range = metric.defaultRange(vehicle)

    val stark = Palette.stark

    val appearanceSection: @Composable () -> Unit = {
        SectionLabel("STYLE")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetType.entries.forEach { t ->
                AppChip(type == t, t.label, onClick = { type = t })
            }
        }

        if (showSize) {
            SectionLabel("SIZE")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppChip(size == WidgetSize.HALF, "Half width", onClick = { size = WidgetSize.HALF })
                AppChip(size == WidgetSize.FULL, "Full width", onClick = { size = WidgetSize.FULL })
            }
        }

        SectionLabel("DATA")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric.entries.forEach { m ->
                AppChip(
                    metric == m,
                    m.label,
                    onClick = {
                        if (metric != m) {
                            metric = m
                            val th = m.defaultThresholds()
                            warnText = th?.first.toFieldText()
                            dangerText = th?.second.toFieldText()
                            minText = ""
                            maxText = ""
                        }
                    },
                )
            }
        }
    }

    val valuesSection: @Composable () -> Unit = {
        OutlinedTextField(
            value = label,
            onValueChange = { label = it.take(20) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Custom label (optional)") },
            placeholder = { Text(metric.label) },
            singleLine = true,
            colors = appTextFieldColors(),
        )

        SectionLabel("SCALE  ·  ${metric.unit(vehicle)}")
        Row {
            NumField("Min", minText, range.start.toFieldText(), Modifier.weight(1f)) { minText = it }
            Spacer(Modifier.width(10.dp))
            NumField("Max", maxText, range.endInclusive.toFieldText(), Modifier.weight(1f)) { maxText = it }
        }
        Text("Blank = automatic for your vehicle.", color = Palette.TextDim, fontSize = 12.sp)

        SectionLabel("ALERT COLORS")
        Row {
            NumField("Warning", warnText, "off", Modifier.weight(1f)) { warnText = it }
            Spacer(Modifier.width(10.dp))
            NumField("Danger", dangerText, "off", Modifier.weight(1f)) { dangerText = it }
        }
        Text(
            "Turns amber at Warning and red at Danger. Set Danger lower than Warning for values where low is bad, like battery.",
            color = Palette.TextDim,
            fontSize = 12.sp,
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Palette.Surface,
        // Stark is always landscape: use the width for two columns.
        sheetMaxWidth = if (stark) 900.dp else BottomSheetDefaults.SheetMaxWidth,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                (if (initial == null) "Add widget" else "Edit widget").caps(),
                fontSize = if (stark) 15.sp else 22.sp,
                fontFamily = Palette.display,
                fontWeight = FontWeight.Bold,
                color = Palette.Fg,
            )

            if (stark) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { appearanceSection() }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { valuesSection() }
                }
            } else {
                appearanceSection()
                valuesSection()
            }

            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                AppButton(
                    "Save",
                    primary = true,
                    onClick = {
                        onSave(
                            DashWidget(
                                id = base.id,
                                type = type,
                                metric = metric,
                                size = size,
                                min = minText.toDoubleOrNull(),
                                max = maxText.toDoubleOrNull(),
                                warn = warnText.toDoubleOrNull(),
                                danger = dangerText.toDoubleOrNull(),
                                label = label.trim().ifBlank { null },
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun NumField(label: String, value: String, placeholder: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        isError = value.isNotBlank() && value.toDoubleOrNull() == null,
        colors = appTextFieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}
