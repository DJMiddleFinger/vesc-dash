package com.vescdash.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vescdash.ble.VescBleTransport
import com.vescdash.data.AppAppearance
import com.vescdash.data.KMH_TO_MPH
import com.vescdash.data.RideStyle
import com.vescdash.data.RideTheme
import com.vescdash.data.VehicleMath
import com.vescdash.data.speedUnit
import com.vescdash.ui.MainViewModel
import com.vescdash.ui.common.AppButton
import com.vescdash.ui.common.AppChip
import com.vescdash.ui.common.Card
import com.vescdash.ui.common.SettingNumber
import com.vescdash.ui.common.SettingSwitch
import com.vescdash.ui.common.appTextFieldColors
import com.vescdash.ui.modes.fmtKw
import com.vescdash.ui.ride.CONTROLLER_RED_C
import com.vescdash.ui.ride.MOTOR_RED_C
import com.vescdash.ui.theme.Palette
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(vm: MainViewModel) {
    val v by vm.vehicle.collectAsStateWithLifecycle()
    val firmware by vm.firmware.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val connected = connection as? VescBleTransport.State.Connected
    val profileName by vm.profileName.collectAsStateWithLifecycle()
    val throttleNote by vm.throttleNote.collectAsStateWithLifecycle()
    val stark = v.appearance == AppAppearance.STARK

    val appearanceCard: @Composable () -> Unit = {
        Card(title = "APPEARANCE") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppAppearance.entries.forEach { a ->
                    AppChip(v.appearance == a, a.label) { vm.updateVehicle { it.copy(appearance = a) } }
                }
            }
            Text(
                if (stark) {
                    "Landscape only. An unofficial lookalike of the Stark Varg app; not affiliated with Stark Future."
                } else {
                    "Stark Varg restyles the whole app and locks it to landscape."
                },
                color = Palette.TextDim,
                fontSize = 12.sp,
            )
        }
    }

    val vehicleCard: @Composable () -> Unit = {
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
    }

    val rideViewCard: @Composable () -> Unit = {
        Card(title = "RIDE VIEW  ·  LANDSCAPE") {
            Text(
                if (stark) "Opens by itself above 5 km/h, or tap RIDE in the side rail." else "Turn the phone sideways to show it.",
                color = Palette.TextDim,
                fontSize = 12.sp,
            )
            Text("Style", color = Palette.Fg, fontSize = 15.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RideStyle.entries.forEach { style ->
                    AppChip(v.rideStyle == style, style.label) { vm.updateVehicle { it.copy(rideStyle = style) } }
                }
            }
            if (v.rideStyle == RideStyle.CUSTOM) {
                Text(
                    "Drag widgets anywhere and pinch to resize them. You can also edit from the ride view while stopped.",
                    color = Palette.TextDim,
                    fontSize = 12.sp,
                )
                AppButton("Edit custom layout", onClick = vm::openLayoutEditor, modifier = Modifier.fillMaxWidth())
            }
            if (!stark) {
                Text("Theme", color = Palette.Fg, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RideTheme.entries.forEach { theme ->
                        AppChip(v.rideTheme == theme, theme.label) { vm.updateVehicle { it.copy(rideTheme = theme) } }
                    }
                }
                Text("Light is easier to read in direct sun. Auto follows the phone's dark mode.", color = Palette.TextDim, fontSize = 12.sp)
            }
            SettingSwitch("Launch animation", v.launchAnimation, "Speed streaks, an edge glow and a screen push when you accelerate hard.") { on ->
                vm.updateVehicle { it.copy(launchAnimation = on) }
            }
        }
    }

    val batteryCard: @Composable () -> Unit = {
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
    }

    val heatCard: @Composable () -> Unit = {
        Card(title = "HEAT WARNINGS  ·  RIDE VIEW") {
            Text(
                "The icon pops up at these temperatures in yellow, then turns red as it heats up — " +
                    "fully red and flashing at ${CONTROLLER_RED_C.roundToInt()} °C (controller) and ${MOTOR_RED_C.roundToInt()} °C (motor). " +
                    "Motor temperature needs a sensor in the motor wired to the VESC and selected in VESC Tool " +
                    "(Motor Temperature Sensor Type); without one it shows --.",
                color = Palette.TextDim,
                fontSize = 12.sp,
            )
            Row {
                SettingNumber("Controller", v.heatWarnControllerC, 20.0..150.0, Modifier.weight(1f), suffix = "°C") { x ->
                    vm.updateVehicle { it.copy(heatWarnControllerC = x) }
                }
                Spacer(Modifier.width(10.dp))
                SettingNumber("Motor", v.heatWarnMotorC, 20.0..150.0, Modifier.weight(1f), suffix = "°C") { x ->
                    vm.updateVehicle { it.copy(heatWarnMotorC = x) }
                }
            }
        }
    }

    val limitsCard: @Composable () -> Unit = {
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
    }

    val throttleCard: @Composable () -> Unit = {
        Card(title = "THROTTLE  ·  PER-MODE RESPONSE") {
            SettingSwitch(
                "Let modes change throttle response",
                v.throttleControl,
                "Adds an editable throttle curve and power build-up time under each mode's Power, and a release ramp in Advanced. Needs the ADC throttle on VESC firmware 6.00 or newer. " +
                    "Changes last until the VESC is powered off; turning this off doesn't undo them.",
            ) { on -> vm.updateVehicle { it.copy(throttleControl = on) } }
            if (v.throttleControl) {
                Text(
                    "Enter your VESC Tool values (App Settings → ADC → General). Modes without their own value use these. " +
                        "After a mode change that alters them, the VESC may need the throttle released for half a second before it responds.",
                    color = Palette.TextDim,
                    fontSize = 12.sp,
                )
                SettingNumber("Throttle exponent", v.throttleExp, -5.0..5.0, help = "0 = linear") { x ->
                    vm.updateVehicle { it.copy(throttleExp = x) }
                }
                Row {
                    SettingNumber("Ramp up", v.rampUpS, 0.0..5.0, Modifier.weight(1f), suffix = "s") { x ->
                        vm.updateVehicle { it.copy(rampUpS = x) }
                    }
                    Spacer(Modifier.width(10.dp))
                    SettingNumber("Ramp down", v.rampDownS, 0.0..5.0, Modifier.weight(1f), suffix = "s") { x ->
                        vm.updateVehicle { it.copy(rampDownS = x) }
                    }
                }
                throttleNote?.let { Text(it, color = Palette.Warn, fontSize = 12.sp) }
            }
        }
    }

    val controllerCard: @Composable () -> Unit = {
        Card(title = "CONTROLLER") {
            if (connected != null && profileName != null) {
                // Commit on blur rather than per key, so the saved name coming back doesn't fight the typing.
                var name by remember(profileName) { mutableStateOf(profileName.orEmpty()) }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("Saved settings for this controller") },
                    singleLine = true,
                    colors = appTextFieldColors(),
                    supportingText = { Text("Vehicle setup and modes are saved per controller and load when you connect to it.") },
                    modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused && name.isNotBlank() && name != profileName) vm.renameProfile(name) },
                )
            }
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
    }

    val connectionCard: @Composable () -> Unit = {
        Card(title = "CONNECTION") {
            Text(
                if (connected != null) "Connected to ${connected.name ?: connected.address}" else "Not connected",
                color = Palette.Fg,
                fontWeight = FontWeight.SemiBold,
            )
            if (firmware != null && connected != null) {
                Text("Firmware $firmware", color = Palette.TextDim, fontSize = 13.sp)
            }
            AppButton("Re-send active mode", onClick = vm::reapplyMode, enabled = connected != null, modifier = Modifier.fillMaxWidth())
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (stark) {
            // Landscape has room for two columns of cards.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    appearanceCard()
                    vehicleCard()
                    rideViewCard()
                    batteryCard()
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    heatCard()
                    limitsCard()
                    throttleCard()
                    controllerCard()
                    connectionCard()
                }
            }
        } else {
            appearanceCard()
            vehicleCard()
            rideViewCard()
            batteryCard()
            heatCard()
            limitsCard()
            throttleCard()
            controllerCard()
            connectionCard()
        }
        Spacer(Modifier.width(1.dp))
    }
}
