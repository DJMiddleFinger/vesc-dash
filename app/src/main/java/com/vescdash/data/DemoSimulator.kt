package com.vescdash.data

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * Generates a plausible ride (a launch, pulls, lifts, braking, a stop every ~70 s) so the UI can
 * be tried without a VESC. Respects the active mode's power, regen and top speed.
 */
class DemoSimulator {
    private var t = 0.0
    private var speedKmh = 0.0
    private var whUsed = 0.0
    private var whRegen = 0.0
    private var ahUsed = 0.0
    private var tacho = 0.0
    private var tempFet = 28.0
    private var tempMotor = 30.0
    private val startSoc = 0.86

    fun step(dt: Double, v: VehicleSettings, mode: DriveMode?): Telemetry {
        t += dt
        val power = (mode?.powerPct ?: 100) / 100.0
        val regen = (mode?.regenPct ?: 50) / 100.0
        val top = min(mode?.topSpeedKmh ?: Double.MAX_VALUE, VehicleMath.maxSpeedKmh(v)).coerceAtLeast(5.0)

        val cycle = t % 70.0
        val throttle = when {
            cycle < 6.0 -> 0.0 // stopped
            cycle < 9.0 -> 1.0 // full-throttle launch away from the stop
            cycle < 60.0 -> (0.55 + 0.45 * sin(t * 0.45) + 0.2 * sin(t * 1.7)).coerceIn(-0.6, 1.0)
            else -> -0.8 // brake to a stop
        }
        val accel = if (throttle >= 0) {
            throttle * power * 14.0 * (1 - speedKmh / top) - 0.8
        } else {
            throttle * regen * 20.0 - 0.8
        }
        speedKmh = (speedKmh + accel * dt).coerceIn(0.0, top)

        val motorMax = v.motorCurrentMax * v.controllers
        val motorCurrent = when {
            speedKmh < 0.5 && throttle <= 0 -> 0.0
            throttle >= 0 -> throttle * power * motorMax
            else -> throttle * regen * motorMax
        }
        val noLoad = VehicleMath.noLoadSpeedKmh(v).coerceAtLeast(1.0)
        val duty = (speedKmh / noLoad).coerceIn(0.0, v.maxDuty)

        val capacityWh = v.nominalVoltage * 30.0
        val soc = (startSoc - (whUsed - whRegen) / capacityWh).coerceIn(0.0, 1.0)
        val batteryCurrent = (motorCurrent * duty)
            .coerceIn(-v.batteryRegenMax * v.controllers, v.batteryCurrentMax * power * v.controllers)
        val voltage = v.cellsSeries * (3.0 + 1.2 * soc) - batteryCurrent * 0.04

        val watts = voltage * batteryCurrent
        if (watts >= 0) {
            whUsed += watts * dt / 3600.0
            ahUsed += batteryCurrent * dt / 3600.0
        } else {
            whRegen += -watts * dt / 3600.0
        }
        val erpm = VehicleMath.erpmForSpeedKmh(speedKmh, v)
        tacho += abs(erpm) / 60.0 * dt * 6.0 // 6 tacho counts per electrical revolution

        val load = abs(motorCurrent) / motorMax.coerceAtLeast(1.0)
        // Hot enough under hard riding to show the heat warnings.
        tempFet += ((30.0 + 65.0 * load) - tempFet) * dt / 20.0
        tempMotor += ((32.0 + 90.0 * load) - tempMotor) * dt / 25.0

        return Telemetry(
            timeMs = System.currentTimeMillis(),
            voltage = voltage,
            batteryCurrent = batteryCurrent,
            motorCurrent = motorCurrent,
            duty = duty,
            erpm = erpm,
            tempFet = tempFet,
            tempMotor = tempMotor,
            ahUsed = ahUsed,
            ahCharged = 0.0,
            whUsed = whUsed,
            whCharged = whRegen,
            tachoAbs = tacho.toLong(),
            fault = 0,
            controllers = v.controllers,
        )
    }
}
