package com.vescdash.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vescdash.ble.VescBleTransport
import com.vescdash.data.KMH_TO_MPH
import com.vescdash.data.RideStyle
import com.vescdash.data.RideTheme
import com.vescdash.data.VehicleMath
import com.vescdash.data.speedUnit
import com.vescdash.ui.MainViewModel
import com.vescdash.ui.common.Card
import com.vescdash.ui.common.SettingNumber
import com.vescdash.ui.common.SettingSwitch
import com.vescdash.ui.modes.fmtKw
import com.vescdash.ui.theme.Palette
import kotlin.math.roundToInt

@Composable
fun SetupScreen(vm: MainViewModel) {
    val v by vm.vehicle.collectAsStateWithLifecycle()
    val firmware by vm.firmware.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val connected = connection as? VescBleTransport.State.Connected

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(title = "VEHICLE") {
            Row {
                SettingNumber("Motor poles", v.motorPoles.toDouble(), 2.0..120.0, Modifier.weight(1f), integer = true) { x ->
                    vm.updateVehicle { it.copy(motorPoles = x.toInt()) }
                }
                Spacer(Modifier.width(10.dp))
                SettingNumber("Motor KV", v.motorKv, 1.0..2000.0, Modifier.weight(1f)) { x ->
                    vm.updateVehicle { it.copy(motorKv = x) }
                }
            }
            Row {
                SettingNumber("Gear ratio", v.gearRatio, 0.1..50.0, Modifier.weight(1f), help = "Hub motor = 1") { x ->
                    vm.updateVehicle { it.copy(gearRatio = x) }
                }
                Spacer(Modifier.width(10.dp))
                SettingNumber("Wheel Ø", v.wheelDiameterMm, 30.0..2000.0, Modifier.weight(1f), suffix = "mm") { x ->
                    vm.updateVehicle { it.copy(wheelDiameterMm = x) }
                }
            }
            SettingNumber("Battery cells in series", v.cellsSeries.toDouble(), 1.0..40.0, integer = true, suffix = "S") { x ->
                vm.updateVehicle { it.copy(cellsSeries = x.toInt()) }
            }
            SettingSwitch("Imperial units", v.imperial, "mph and miles") { on -> vm.updateVehicle { it.copy(imperial = on) } }

            val factor = if (v.imperial) KMH_TO_MPH else 1.0
            val topSpeed = (VehicleMath.maxSpeedKmh(v) * factor).roundToInt()
            val peak = VehicleMath.peakKw(null, v)
            Text(
                "Estimated at 100%: top speed $topSpeed ${speedUnit(v)} · ${fmtKw(peak)} kW (${VehicleMath.kwToHp(peak).roundToInt()} hp)",
                color = Palette.TextDim,
                fontSize = 12.sp,
            )
        }

        Card(title = "RIDE VIEW  ·  LANDSCAPE") {
            Text("Turn the phone sideways to show it.", color = Palette.TextDim, fontSize = 12.sp)
            Text("Style", color = Palette.Fg, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RideStyle.entries.forEach { style ->
                    FilterChip(
                        selected = v.rideStyle == style,
                        onClick = { vm.updateVehicle { it.copy(rideStyle = style) } },
                        label = { Text(style.label) },
                    )
                }
            }
            Text("Theme", color = Palette.Fg, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RideTheme.entries.forEach { theme ->
                    FilterChip(
                        selected = v.rideTheme == theme,
                        onClick = { vm.updateVehicle { it.copy(rideTheme = theme) } },
                        label = { Text(theme.label) },
                    )
                }
            }
            Text("Light is easier to read in direct sun. Auto follows the phone's dark mode.", color = Palette.TextDim, fontSize = 12.sp)
        }

        Card(title = "BATTERY") {
            SettingSwitch(
                "Stabilize battery %",
                v.batteryStabilize,
                "Compensates for voltage sag so the reading doesn't drop when you accelerate",
            ) { on -> vm.updateVehicle { it.copy(batteryStabilize = on) } }
            SettingNumber(
                "Pack capacity",
                v.batteryCapacityAh,
                0.0..500.0,
                suffix = "Ah",
                help = "Optional. With capacity set, charge used is counted directly for the steadiest reading. 0 = unknown.",
            ) { x -> vm.updateVehicle { it.copy(batteryCapacityAh = x) } }
        }

        Card(title = "BASE LIMITS  ·  FROM VESC TOOL") {
            Text(
                "Enter the same values as your VESC Tool motor config. Drive modes scale down from these and never go above them.",
                color = Palette.TextDim,
                fontSize = 12.sp,
            )
            Row {
                SettingNumber("Motor current max", v.motorCurrentMax, 1.0..500.0, Modifier.weight(1f), suffix = "A") { x ->
                    vm.updateVehicle { it.copy(motorCurrentMax = x) }
                }
                Spacer(Modifier.width(10.dp))
                SettingNumber("Battery current max", v.batteryCurrentMax, 1.0..500.0, Modifier.weight(1f), suffix = "A") { x ->
                    vm.updateVehicle { it.copy(batteryCurrentMax = x) }
                }
            }
            Row {
                SettingNumber("Battery regen max", v.batteryRegenMax, 0.0..300.0, Modifier.weight(1f), suffix = "A", help = "Positive number") { x ->
                    vm.updateVehicle { it.copy(batteryRegenMax = x) }
                }
                Spacer(Modifier.width(10.dp))
                SettingNumber("Max duty", v.maxDuty, 0.1..0.99, Modifier.weight(1f), help = "e.g. 0.95") { x ->
                    vm.updateVehicle { it.copy(maxDuty = x) }
                }
            }
            SettingNumber("Max ERPM", v.maxErpm, 1000.0..500000.0, integer = true) { x ->
                vm.updateVehicle { it.copy(maxErpm = x) }
            }
            Text("Current limits are per controller.", color = Palette.TextDim, fontSize = 12.sp)
        }

        Card(title = "CONTROLLER") {
            SettingSwitch("Dual controller (CAN)", v.dualController, "Read and tune a second VESC over CAN") { on ->
                vm.updateVehicle { it.copy(dualController = on) }
            }
            if (v.dualController) {
                SettingNumber("Second controller CAN ID", v.canSlaveId.toDouble(), 0.0..254.0, integer = true) { x ->
                    vm.updateVehicle { it.copy(canSlaveId = x.toInt()) }
                }
            }
            SettingSwitch("Apply mode on connect", v.applyModeOnConnect, "Re-send the active mode every time the link comes up") { on ->
                vm.updateVehicle { it.copy(applyModeOnConnect = on) }
            }
            SettingSwitch("Keep screen on", v.keepScreenOn, "While connected") { on ->
                vm.updateVehicle { it.copy(keepScreenOn = on) }
            }
            SettingSwitch("Demo mode", v.demoMode, "Play simulated ride data when no VESC is connected") { on ->
                vm.updateVehicle { it.copy(demoMode = on) }
            }
            SettingNumber("Update rate", v.pollHz.toDouble(), 1.0..30.0, integer = true, suffix = "Hz", help = "10 Hz is plenty over BLE") { x ->
                vm.updateVehicle { it.copy(pollHz = x.toInt()) }
            }
        }

        Card(title = "CONNECTION") {
            Text(
                if (connected != null) "Connected to ${connected.name ?: connected.address}" else "Not connected",
                color = Palette.Fg,
                fontWeight = FontWeight.SemiBold,
            )
            if (firmware != null && connected != null) {
                Text("Firmware $firmware", color = Palette.TextDim, fontSize = 13.sp)
            }
            OutlinedButton(onClick = vm::reapplyMode, enabled = connected != null, modifier = Modifier.fillMaxWidth()) {
                Text("Re-send active mode")
            }
        }
        Spacer(Modifier.width(1.dp))
    }
}
