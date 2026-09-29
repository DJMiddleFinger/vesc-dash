package com.vescdash.ui.modes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vescdash.data.DriveMode
import com.vescdash.data.KMH_TO_MPH
import com.vescdash.data.VehicleMath
import com.vescdash.data.VehicleSettings
import com.vescdash.data.speedUnit
import com.vescdash.ui.MainViewModel
import com.vescdash.ui.common.SectionLabel
import com.vescdash.ui.theme.Palette
import java.util.Locale
import kotlin.math.roundToInt

const val MAX_MODES = 6

@Composable
fun ModesScreen(vm: MainViewModel) {
    val modes by vm.modes.collectAsStateWithLifecycle()
    val activeId by vm.activeModeId.collectAsStateWithLifecycle()
    val vehicle by vm.vehicle.collectAsStateWithLifecycle()
    var tuningId by rememberSaveable { mutableStateOf<String?>(null) }

    val effectiveActive = modes.firstOrNull { it.id == activeId }?.id ?: modes.firstOrNull()?.id
    val tuning = tuningId?.let { id -> modes.firstOrNull { it.id == id } }

    if (tuning != null) {
        BackHandler { tuningId = null }
        TunerScreen(
            mode = tuning,
            index = modes.indexOf(tuning),
            vehicle = vehicle,
            isActive = tuning.id == effectiveActive,
            canDelete = modes.size > 1,
            onBack = { tuningId = null },
            onSave = { m, activate -> vm.saveMode(m, activate) },
            onDelete = {
                vm.deleteMode(tuning.id)
                tuningId = null
            },
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("DRIVE MODES", color = Palette.Fg, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text(
                "Tap a mode to ride it. Use the tune button to set power, regen and top speed.",
                color = Palette.TextDim,
                fontSize = 13.sp,
            )
        }
        itemsIndexed(modes, key = { _, m -> m.id }) { i, m ->
            ModeCard(
                index = i,
                mode = m,
                vehicle = vehicle,
                active = m.id == effectiveActive,
                onSelect = { vm.selectMode(m.id) },
                onTune = { tuningId = m.id },
            )
        }
        if (modes.size < MAX_MODES) {
            item {
                OutlinedButton(onClick = { tuningId = vm.addMode() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(" Add mode")
                }
            }
        }
        item { SafetyNote() }
    }
}

@Composable
private fun ModeCard(
    index: Int,
    mode: DriveMode,
    vehicle: VehicleSettings,
    active: Boolean,
    onSelect: () -> Unit,
    onTune: () -> Unit,
) {
    val c = Color(mode.color)
    val shape = RoundedCornerShape(20.dp)
    val kw = remember(mode, vehicle) { VehicleMath.peakKw(mode, vehicle) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (active) c.copy(alpha = 0.10f) else Palette.Surface)
            .border(if (active) 2.dp else 1.dp, if (active) c else Palette.Outline, shape)
            .clickable(onClick = onSelect)
            .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${index + 1}", color = c, fontSize = 44.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(40.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(mode.name.uppercase(), color = Palette.Fg, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                if (active) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "ACTIVE",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(c).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                "${fmtKw(kw)} kW · ${VehicleMath.kwToHp(kw).roundToInt()} hp",
                color = Palette.Fg,
                fontSize = 13.sp,
            )
            Text(
                "Power ${mode.powerPct}% · Regen ${mode.regenPct}% · ${topSpeedText(mode, vehicle)}",
                color = Palette.TextDim,
                fontSize = 12.sp,
            )
        }
        PowerCurveChart(mode, vehicle, Modifier.size(width = 72.dp, height = 44.dp), detailed = false)
        IconButton(onClick = onTune) { Icon(Icons.Filled.Tune, contentDescription = "Tune ${mode.name}", tint = Palette.Fg) }
    }
}

@Composable
fun SafetyNote() {
    Column(Modifier.padding(top = 8.dp)) {
        SectionLabel("HOW MODES WORK")
        Text(
            "Selecting a mode sends temporary limits to the VESC (not saved to flash — power-cycling restores your VESC Tool config). " +
                "Modes only scale down from the base limits in Setup, so set those to match your VESC Tool motor config. " +
                "Regen also sets your electric brake strength.",
            color = Palette.TextDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

fun fmtKw(kw: Double): String = String.format(Locale.US, if (kw < 10) "%.1f" else "%.0f", kw)

fun topSpeedText(mode: DriveMode, v: VehicleSettings): String {
    val kmh = mode.topSpeedKmh ?: return "No speed limit"
    val factor = if (v.imperial) KMH_TO_MPH else 1.0
    return "Max ${(kmh * factor).roundToInt()} ${speedUnit(v)}"
}
