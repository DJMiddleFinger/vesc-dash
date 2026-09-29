package com.vescdash.data

import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

@Serializable
enum class Metric(val label: String, val decimals: Int) {
    SPEED("Speed", 0),
    POWER("Power", 1),
    BATTERY("Battery", 0),
    VOLTAGE("Voltage", 1),
    BATTERY_CURRENT("Battery current", 1),
    MOTOR_CURRENT("Motor current", 1),
    DUTY("Duty cycle", 0),
    MOTOR_RPM("Motor RPM", 0),
    TEMP_FET("Controller temp", 0),
    TEMP_MOTOR("Motor temp", 0),
    TRIP("Trip", 2),
    EFFICIENCY("Efficiency", 1),
    WH_USED("Energy used", 1),
    WH_REGEN("Energy regen", 1),
    AH_USED("Charge used", 2),
}

private fun distFactor(v: VehicleSettings) = if (v.imperial) KMH_TO_MPH else 1.0

fun speedUnit(v: VehicleSettings) = if (v.imperial) "mph" else "km/h"

fun Metric.unit(v: VehicleSettings): String = when (this) {
    Metric.SPEED -> speedUnit(v)
    Metric.POWER -> "kW"
    Metric.BATTERY, Metric.DUTY -> "%"
    Metric.VOLTAGE -> "V"
    Metric.BATTERY_CURRENT, Metric.MOTOR_CURRENT -> "A"
    Metric.MOTOR_RPM -> "rpm"
    Metric.TEMP_FET, Metric.TEMP_MOTOR -> "°C"
    Metric.TRIP -> if (v.imperial) "mi" else "km"
    Metric.EFFICIENCY -> if (v.imperial) "Wh/mi" else "Wh/km"
    Metric.WH_USED, Metric.WH_REGEN -> "Wh"
    Metric.AH_USED -> "Ah"
}

fun Metric.value(t: Telemetry, v: VehicleSettings): Double = when (this) {
    Metric.SPEED -> abs(VehicleMath.speedKmhForErpm(t.erpm, v)) * distFactor(v)
    Metric.POWER -> t.voltage * t.batteryCurrent / 1000.0
    Metric.BATTERY -> if (!t.batteryPct.isNaN()) t.batteryPct else Battery.percent(t.voltage / v.cellsSeries.coerceAtLeast(1))
    Metric.VOLTAGE -> t.voltage
    Metric.BATTERY_CURRENT -> t.batteryCurrent
    Metric.MOTOR_CURRENT -> t.motorCurrent
    Metric.DUTY -> abs(t.duty) * 100.0
    Metric.MOTOR_RPM -> abs(t.erpm) / (v.motorPoles.coerceAtLeast(2) / 2.0)
    Metric.TEMP_FET -> t.tempFet
    Metric.TEMP_MOTOR -> t.tempMotor
    Metric.TRIP -> VehicleMath.tripKm(t, v) * distFactor(v)
    Metric.EFFICIENCY -> {
        val km = VehicleMath.tripKm(t, v)
        if (km < 0.05) 0.0 else (t.whUsed - t.whCharged) / (km * distFactor(v))
    }
    Metric.WH_USED -> t.whUsed
    Metric.WH_REGEN -> t.whCharged
    Metric.AH_USED -> t.ahUsed
}

fun Metric.defaultRange(v: VehicleSettings): ClosedFloatingPointRange<Double> = when (this) {
    Metric.SPEED -> 0.0..niceCeil(VehicleMath.maxSpeedKmh(v) * distFactor(v))
    Metric.POWER -> 0.0..niceCeil(VehicleMath.peakKw(null, v))
    Metric.BATTERY, Metric.DUTY -> 0.0..100.0
    Metric.VOLTAGE -> (v.cellsSeries * 3.0)..(v.cellsSeries * 4.2)
    Metric.BATTERY_CURRENT -> 0.0..niceCeil(v.batteryCurrentMax * v.controllers)
    Metric.MOTOR_CURRENT -> 0.0..niceCeil(v.motorCurrentMax * v.controllers)
    Metric.MOTOR_RPM -> 0.0..niceCeil(v.motorKv * v.nominalVoltage)
    Metric.TEMP_FET -> 0.0..100.0
    Metric.TEMP_MOTOR -> 0.0..120.0
    Metric.TRIP -> 0.0..niceCeil(50.0 * distFactor(v))
    Metric.EFFICIENCY -> 0.0..niceCeil(50.0 / distFactor(v))
    Metric.WH_USED -> 0.0..500.0
    Metric.WH_REGEN -> 0.0..100.0
    Metric.AH_USED -> 0.0..20.0
}

/** (warning, danger). Danger below warning means "low is bad". */
fun Metric.defaultThresholds(): Pair<Double, Double>? = when (this) {
    Metric.BATTERY -> 20.0 to 10.0
    Metric.DUTY -> 85.0 to 92.0
    Metric.TEMP_FET -> 70.0 to 85.0
    Metric.TEMP_MOTOR -> 80.0 to 100.0
    else -> null
}

fun Metric.format(value: Double?): String {
    if (value == null || value.isNaN()) return "--"
    val v = if (abs(value) < 0.5 / 10.0.pow(decimals)) 0.0 else value
    return String.format(Locale.US, "%.${decimals}f", v)
}
