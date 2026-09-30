package com.vescdash.ui.modes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vescdash.data.DriveMode
import com.vescdash.data.KMH_TO_MPH
import com.vescdash.data.VehicleMath
import com.vescdash.data.VehicleSettings
import com.vescdash.data.speedUnit
import com.vescdash.ui.MainViewModel
import com.vescdash.ui.common.AppButton
import com.vescdash.ui.common.Card
import com.vescdash.ui.common.StarkSlider
import com.vescdash.ui.common.StarkToast
import com.vescdash.ui.common.StarkWell
import com.vescdash.ui.common.ValuePill
import com.vescdash.ui.common.appSwitchColors
import com.vescdash.ui.common.appTextFieldColors
import com.vescdash.ui.theme.HeavyWideFont
import com.vescdash.ui.theme.Palette
import com.vescdash.ui.theme.WideFont
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Stark's power-mode editor in landscape: the mode chips down the left, and the chosen mode's
 * parameters as cards with tick-scale sliders on the right. It replaces Classic's list-then-tuner pair.
 */
@Composable
internal fun StarkModesScreen(vm: MainViewModel) {
    val modes by vm.modes.collectAsStateWithLifecycle()
    val activeId by vm.activeModeId.collectAsStateWithLifecycle()
    val vehicle by vm.vehicle.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2200)
            toast = null
        }
    }

    val active = modes.firstOrNull { it.id == activeId } ?: modes.firstOrNull()
    val mode = modes.firstOrNull { it.id == selectedId } ?: active ?: return

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .width(148.dp)
                    .background(Color(0xFF151515))
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                modes.forEachIndexed { i, m ->
                    ModeTab(i + 1, m, selected = m.id == mode.id, active = m.id == active?.id) { selectedId = m.id }
                }
                if (modes.size < MAX_MODES) {
                    Text(
                        "+ ADD",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .clickable { selectedId = vm.addMode() }
                            .padding(vertical = 9.dp),
                        color = Palette.TextDim,
                        fontFamily = WideFont,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            StarkModeEditor(
                mode = mode,
                index = modes.indexOf(mode),
                vehicle = vehicle,
                isActive = mode.id == active?.id,
                canDelete = modes.size > 1,
                onSave = { m, activate ->
                    vm.saveMode(m, activate)
                    toast = "${m.name} saved" + if (activate) " and active." else "."
                },
                onDelete = {
                    vm.deleteMode(mode.id)
                    selectedId = null
                },
                modifier = Modifier.weight(1f),
            )
        }
        StarkToast(toast, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }
}

@Composable
private fun ModeTab(number: Int, mode: DriveMode, selected: Boolean, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(if (selected) Palette.Surface2 else Color(0xFF151515))
            .border(1.dp, if (selected) Palette.Fg.copy(alpha = 0.85f) else Color(0xFF3A3A3A), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(mode.color)))
        Text(
            "MODE $number",
            modifier = Modifier.weight(1f),
            color = if (selected) Palette.Fg else Palette.TextDim,
            fontFamily = WideFont,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (active) Palette.Good else Color.Transparent))
    }
}

@Composable
private fun StarkModeEditor(
    mode: DriveMode,
    index: Int,
    vehicle: VehicleSettings,
    isActive: Boolean,
    canDelete: Boolean,
    onSave: (DriveMode, Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
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
    val maxDisplaySpeed = (maxSpeedKmh * speedFactor).toFloat()

    Box(modifier.fillMaxHeight()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = draft.name,
                            onValueChange = { draft = draft.copy(name = it.take(12)) },
                            label = { Text("Name") },
                            singleLine = true,
                            colors = appTextFieldColors(),
                            modifier = Modifier.width(220.dp),
                        )
                        ColorRow(selected = draft.color) { draft = draft.copy(color = it) }
                    }
                    Column(Modifier.padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AppButton(if (isActive) "Apply" else "Ride", onClick = { onSave(draft, true) }, primary = true)
                        if (canDelete) {
                            IconButton(onClick = { confirmDelete = true }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete mode", tint = Palette.TextDim)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("MODE", color = Palette.TextDim, fontFamily = WideFont, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp, end = 8.dp))
                        Text("${index + 1}", color = Palette.Fg, fontFamily = HeavyWideFont, fontSize = 40.sp)
                    }
                }
            }

            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ParamCard(
                    Modifier.weight(1f).fillMaxHeight(), Icons.Filled.Bolt, "Power", "% of base limits", "${draft.powerPct}",
                    draft.powerPct.toFloat(), 10f, 100f, 5f, Palette.Cyan,
                ) { draft = draft.copy(powerPct = it.roundToInt()) }
                ParamCard(
                    Modifier.weight(1f).fillMaxHeight(), Icons.Filled.Autorenew, "Regenerative braking", "% of base limits", "${draft.regenPct}",
                    draft.regenPct.toFloat(), 10f, 100f, 5f, Palette.Red,
                ) { draft = draft.copy(regenPct = it.roundToInt()) }
            }

            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(Modifier.weight(1f).fillMaxHeight(), title = "ESTIMATED PEAK POWER") {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(fmtKw(animatedKw.toDouble()), color = c, fontFamily = HeavyWideFont, fontSize = 34.sp)
                        Text(" kW", color = Palette.TextDim, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Text(
                        "${VehicleMath.kwToHp(animatedKw.toDouble()).roundToInt()} hp · ${topSpeedText(draft, vehicle)}",
                        color = Palette.Fg,
                        fontSize = 13.sp,
                    )
                    Text("Power vs speed · dashed = 100% base limits", color = Palette.TextDim, fontSize = 11.sp)
                }
                Card(Modifier.weight(1f).fillMaxHeight()) {
                    PowerCurveChart(draft, vehicle, Modifier.fillMaxWidth().height(120.dp), detailed = true)
                }
            }

            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LimitCard(
                    Modifier.weight(1f).fillMaxHeight(), "Top speed", "Caps motor ERPM", draft.topSpeedKmh != null,
                    draft.topSpeedKmh?.let { "${(it * speedFactor).roundToInt()} $unit" } ?: "OFF",
                    ((draft.topSpeedKmh ?: maxSpeedKmh) * speedFactor).toFloat().coerceIn(5f, maxDisplaySpeed), 5f, maxDisplaySpeed, 1f, c,
                    onToggle = { on -> draft = draft.copy(topSpeedKmh = if (on) maxSpeedKmh * 0.7 else null) },
                ) { draft = draft.copy(topSpeedKmh = it / speedFactor) }
                LimitCard(
                    Modifier.weight(1f).fillMaxHeight(), "Power cap", "Caps battery power", draft.powerCapKw != null,
                    draft.powerCapKw?.let { "${fmtKw(it)} kW" } ?: "OFF",
                    (draft.powerCapKw ?: fullKw).toFloat().coerceIn(0.5f, fullKw.toFloat()), 0.5f, fullKw.toFloat(), 0.5f, c,
                    onToggle = { on -> draft = draft.copy(powerCapKw = if (on) (fullKw * 0.6 * 2).roundToInt() / 2.0 else null) },
                ) { draft = draft.copy(powerCapKw = it.toDouble()) }
            }

            SafetyNote()
            Spacer(Modifier.height(64.dp))
        }

        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(if (dirty) Color(0xFFD0D0D0) else Palette.Surface2)
                .clickable(enabled = dirty) { onSave(draft, false) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Save, contentDescription = "Save", tint = if (dirty) Color(0xFF1A1A1C) else Palette.TextDim)
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

/** One parameter of the mode: icon, title and unit on the left, its value pill on the right, the slider in a well below. */
@Composable
private fun ParamCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    unit: String,
    valueText: String,
    value: Float,
    min: Float,
    max: Float,
    step: Float,
    color: Color,
    onValue: (Float) -> Unit,
) {
    Card(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Palette.TextDim, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(title, color = Palette.Fg, fontSize = 16.sp)
                Text(unit, color = Palette.TextDim, fontSize = 11.sp)
            }
            ValuePill(valueText)
        }
        StarkWell { StarkSlider(value, onValue, min, max, color, step = step) }
    }
}

/** A limit that can be switched off; its slider shows only while it is on. */
@Composable
private fun LimitCard(
    modifier: Modifier,
    title: String,
    help: String,
    enabled: Boolean,
    valueText: String,
    value: Float,
    min: Float,
    max: Float,
    step: Float,
    color: Color,
    onToggle: (Boolean) -> Unit,
    onValue: (Float) -> Unit,
) {
    Card(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Palette.Fg, fontSize = 16.sp)
                Text(help, color = Palette.TextDim, fontSize = 11.sp)
            }
            ValuePill(valueText, color = if (enabled) Palette.Fg else Palette.TextDim)
            Spacer(Modifier.width(10.dp))
            Switch(checked = enabled, onCheckedChange = onToggle, colors = appSwitchColors(color))
        }
        if (enabled) StarkWell { StarkSlider(value, onValue, min, max, color, step = step) }
    }
}
