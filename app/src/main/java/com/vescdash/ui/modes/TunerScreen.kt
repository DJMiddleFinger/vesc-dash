package com.vescdash.ui.modes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vescdash.data.Defaults
import com.vescdash.data.DriveMode
import com.vescdash.data.KMH_TO_MPH
import com.vescdash.data.VehicleMath
import com.vescdash.data.VehicleSettings
import com.vescdash.data.speedUnit
import com.vescdash.ui.common.SectionLabel
import com.vescdash.ui.theme.NumberStyle
import com.vescdash.ui.theme.Palette
import kotlin.math.roundToInt

@Composable
fun TunerScreen(
    mode: DriveMode,
    index: Int,
    vehicle: VehicleSettings,
    isActive: Boolean,
    canDelete: Boolean,
    onBack: () -> Unit,
    onSave: (DriveMode, Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var draft by remember(mode.id) { mutableStateOf(mode) }
    var confirmDelete by remember { mutableStateOf(false) }
    val dirty = draft != mode
    val c = Color(draft.color)

    val maxSpeedKmh = remember(vehicle) { VehicleMath.maxSpeedKmh(vehicle).coerceAtLeast(6.0) }
    val fullKw = remember(vehicle) { VehicleMath.peakKw(null, vehicle).coerceAtLeast(2.0) }
    val peakKw = remember(draft, vehicle) { VehicleMath.peakKw(draft, vehicle) }
    val animatedKw by animateFloatAsState(peakKw.toFloat(), label = "peakKw")
    val speedFactor = if (vehicle.imperial) KMH_TO_MPH else 1.0
    val unit = speedUnit(vehicle)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Palette.Fg)
            }
            Text(
                "TUNE MODE ${index + 1}",
                color = Palette.Fg,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                modifier = Modifier.weight(1f),
            )
            if (dirty) Text("unsaved", color = Palette.Warn, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
            if (canDelete) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete mode", tint = Palette.TextDim)
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it.take(12)) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ColorRow(selected = draft.color) { draft = draft.copy(color = it) }

            // Hero readout
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        fmtKw(animatedKw.toDouble()),
                        style = NumberStyle,
                        color = c,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(" kW", color = Palette.TextDim, fontSize = 18.sp, modifier = Modifier.padding(bottom = 12.dp))
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 10.dp)) {
                        Text(
                            "${VehicleMath.kwToHp(animatedKw.toDouble()).roundToInt()} HP",
                            color = Palette.Fg,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(topSpeedText(draft, vehicle), color = Palette.TextDim, fontSize = 12.sp)
                    }
                }
                SectionLabel("ESTIMATED PEAK POWER")
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Palette.Surface)
                    .padding(12.dp),
            ) {
                PowerCurveChart(draft, vehicle, Modifier.fillMaxWidth().height(190.dp), detailed = true)
                Text(
                    "Power vs speed · dashed = 100% base limits",
                    color = Palette.TextDim,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            TuneSlider(
                title = "POWER",
                valueText = "${draft.powerPct}%",
                help = "Motor torque and battery draw",
                value = draft.powerPct.toFloat(),
                range = 1f..100f,
                steps = 0,
                color = c,
            ) { draft = draft.copy(powerPct = snapPower(it)) }

            if (vehicle.throttleControl) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Palette.Surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    SectionLabel("THROTTLE RESPONSE", color = Palette.Fg)
                    Text("Drag the curve: higher gives power sooner, lower later", color = Palette.TextDim, fontSize = 12.sp)
                    ThrottleCurveEditor(
                        draft.throttleExp, vehicle.throttleExp, c, { draft = draft.copy(throttleExp = it) },
                        Modifier.fillMaxWidth().height(190.dp).padding(top = 8.dp),
                    )
                }
                powerBuildField(draft, vehicle) { draft = it }?.let { FieldSlider(it, c) }
            } else {
                Text("Turn on Setup → Throttle to shape how power comes on.", color = Palette.TextDim, fontSize = 12.sp)
            }

            TuneSlider(
                title = "REGEN BRAKING",
                valueText = "${draft.regenPct}%",
                help = "Electric brake strength (brake lever and coast regen). For regen when you let off the throttle, see \"Regen when you let off the throttle\" in the README.",
                value = draft.regenPct.toFloat(),
                range = 10f..100f,
                steps = 17,
                color = c,
            ) { draft = draft.copy(regenPct = (it / 5f).roundToInt() * 5) }

            val maxDisplaySpeed = (maxSpeedKmh * speedFactor).toFloat()
            LimitSlider(
                title = "TOP SPEED",
                enabled = draft.topSpeedKmh != null,
                valueText = draft.topSpeedKmh?.let { "${(it * speedFactor).roundToInt()} $unit" } ?: "No limit",
                help = "Caps motor ERPM",
                value = ((draft.topSpeedKmh ?: maxSpeedKmh) * speedFactor).toFloat().coerceIn(5f, maxDisplaySpeed),
                range = 5f..maxDisplaySpeed,
                color = c,
                onToggle = { on -> draft = draft.copy(topSpeedKmh = if (on) maxSpeedKmh * 0.7 else null) },
                onValue = { draft = draft.copy(topSpeedKmh = it.roundToInt() / speedFactor) },
            )

            LimitSlider(
                title = "POWER CAP",
                enabled = draft.powerCapKw != null,
                valueText = draft.powerCapKw?.let { "${fmtKw(it)} kW" } ?: "No cap",
                help = "Hard limit on battery power",
                value = (draft.powerCapKw ?: fullKw).toFloat().coerceIn(0.5f, fullKw.toFloat()),
                range = 0.5f..fullKw.toFloat(),
                color = c,
                onToggle = { on -> draft = draft.copy(powerCapKw = if (on) (fullKw * 0.6 * 2).roundToInt() / 2.0 else null) },
                onValue = { draft = draft.copy(powerCapKw = (it * 2).roundToInt() / 2.0) },
            )

            var showAdvanced by remember(mode.id) { mutableStateOf(advancedFields(mode, vehicle) {}.any { it.enabled }) }
            TextButton(onClick = { showAdvanced = !showAdvanced }) { Text(if (showAdvanced) "HIDE ADVANCED" else "SHOW ADVANCED") }
            if (showAdvanced) {
                advancedFields(draft, vehicle) { draft = it }.forEach { FieldSlider(it, c) }
            }

            SafetyNote()
            Spacer(Modifier.height(4.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = { onSave(draft, false) },
                enabled = dirty,
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text("SAVE") }
            Button(
                onClick = { onSave(draft, true) },
                modifier = Modifier.weight(1.6f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c, contentColor = Color.Black),
            ) {
                Text(if (isActive) "SAVE & APPLY" else "SAVE & RIDE", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Palette.Dialog,
            title = { Text("Delete ${mode.name}?") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = Palette.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun ColorRow(selected: Long, onPick: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Defaults.modeColors.forEach { argb ->
            val isSel = argb == selected
            Spacer(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(if (isSel) 3.dp else 0.dp, if (isSel) Palette.Fg else Color.Transparent, CircleShape)
                    .clickable { onPick(argb) },
            )
        }
    }
}

@Composable
private fun TuneSlider(
    title: String,
    valueText: String,
    help: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    color: Color,
    onValue: (Float) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                SectionLabel(title, color = Palette.Fg)
                Text(help, color = Palette.TextDim, fontSize = 12.sp)
            }
            Text(valueText, style = NumberStyle, color = color, fontSize = 28.sp, fontWeight = FontWeight.Black)
        }
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = Palette.Outline,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun FieldSlider(f: AdvancedField, color: Color) = LimitSlider(
    title = f.title.uppercase(),
    enabled = f.enabled,
    valueText = f.valueText,
    help = f.help,
    value = f.value,
    range = f.min..f.max,
    color = color,
    onToggle = f.onToggle,
    onValue = f.onValue,
)

@Composable
private fun LimitSlider(
    title: String,
    enabled: Boolean,
    valueText: String,
    help: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    color: Color,
    onToggle: (Boolean) -> Unit,
    onValue: (Float) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel(title, color = Palette.Fg)
                Text(help, color = Palette.TextDim, fontSize = 12.sp)
            }
            Text(
                valueText,
                style = NumberStyle,
                color = if (enabled) color else Palette.TextDim,
                fontSize = if (enabled) 24.sp else 16.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = color, checkedThumbColor = Color.Black),
            )
        }
        if (enabled) {
            Slider(
                value = value,
                onValueChange = onValue,
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = color,
                    activeTrackColor = color,
                    inactiveTrackColor = Palette.Outline,
                ),
            )
        }
    }
}
