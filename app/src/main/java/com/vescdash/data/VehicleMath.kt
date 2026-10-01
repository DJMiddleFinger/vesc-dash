package com.vescdash.data

import com.vescdash.vesc.TempLimits
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

const val KMH_TO_MPH = 0.621371

data class CurvePoint(val kmh: Double, val kw: Double)

object VehicleMath {
    private fun polePairs(v: VehicleSettings) = max(v.motorPoles, 2) / 2.0
    private fun gear(v: VehicleSettings) = max(v.gearRatio, 1e-3)
    private fun wheelM(v: VehicleSettings) = max(v.wheelDiameterMm, 1.0) / 1000.0

    fun speedKmhForErpm(erpm: Double, v: VehicleSettings): Double =
        erpm / polePairs(v) / gear(v) * PI * wheelM(v) / 60.0 * 3.6

    fun erpmForSpeedKmh(kmh: Double, v: VehicleSettings): Double =
        kmh / 3.6 * 60.0 / (PI * wheelM(v)) * gear(v) * polePairs(v)

    /** Same scaling the firmware uses for distance: tacho / (3 * poles * gear) * wheel circumference. */
    fun tripKm(t: Telemetry, v: VehicleSettings): Double =
        t.tachoAbs / (3.0 * max(v.motorPoles, 2)) / gear(v) * PI * wheelM(v) / 1000.0

    /** Unloaded speed at 100% duty from motor KV and nominal pack voltage. */
    fun noLoadSpeedKmh(v: VehicleSettings): Double =
        speedKmhForErpm(v.motorKv * v.nominalVoltage * polePairs(v), v)

    /** Highest reachable speed: the smaller of the ERPM limit and the duty-cycle limit. */
    fun maxSpeedKmh(v: VehicleSettings): Double =
        min(speedKmhForErpm(v.maxErpm, v), noLoadSpeedKmh(v) * v.maxDuty)

    /**
     * Estimated electrical power vs speed. Below base speed the motor-current limit
     * dominates (P ≈ I_motor × V_bat × duty); above it the battery-current limit or the
     * mode's power cap flattens the curve; it ends at the top-speed / ERPM limit.
     */
    fun powerCurve(mode: DriveMode?, v: VehicleSettings, samples: Int = 48): List<CurvePoint> {
        val p = (mode?.powerPct ?: 100) / 100.0
        val vNom = v.nominalVoltage
        val noLoad = noLoadSpeedKmh(v)
        val end = minOf(mode?.topSpeedKmh ?: Double.MAX_VALUE, maxSpeedKmh(v), noLoad * dutyMax(mode, v)).coerceAtLeast(0.1)
        val batteryW = v.batteryCurrentMax * (mode?.batteryPct?.div(100.0) ?: p) * vNom * v.controllers
        val capW = mode?.powerCapKw?.let { it * 1000.0 } ?: Double.MAX_VALUE
        val points = (0..samples).map { i ->
            val s = end * i / samples
            val duty = if (noLoad > 0) s / noLoad else 0.0
            val motorW = v.motorCurrentMax * p * vNom * duty * v.controllers
            CurvePoint(s, minOf(motorW, batteryW, capW) / 1000.0)
        }
        return points + CurvePoint(end, 0.0)
    }

    /** The mode's max duty cycle (0..1), never above the base. */
    fun dutyMax(mode: DriveMode?, v: VehicleSettings): Double =
        mode?.maxDutyPct?.let { min(it / 100.0, v.maxDuty) } ?: v.maxDuty

    fun peakKw(mode: DriveMode?, v: VehicleSettings): Double = powerCurve(mode, v).maxOf { it.kw }

    /** Translate a drive mode into the COMM_SET_MCCONF_TEMP limits. */
    fun limitsFor(mode: DriveMode, v: VehicleSettings): TempLimits {
        val power = (mode.powerPct / 100.0).coerceIn(0.01, 1.0)
        val regen = (mode.regenPct / 100.0).coerceIn(0.05, 1.0)
        val battery = (mode.batteryPct?.let { it / 100.0 } ?: power).coerceIn(0.01, 1.0)
        val regenCharge = (mode.regenChargePct?.let { it / 100.0 } ?: regen).coerceIn(0.05, 1.0)
        val erpm = mode.topSpeedKmh?.let { min(erpmForSpeedKmh(it, v), v.maxErpm) } ?: v.maxErpm
        return TempLimits(
            currentMinScale = regen,
            currentMaxScale = power,
            erpmMin = -erpm,
            erpmMax = erpm,
            dutyMin = 0.005,
            dutyMax = dutyMax(mode, v),
            wattMin = -(mode.regenCapKw?.let { it * 1000.0 } ?: 1_500_000.0),
            wattMax = mode.powerCapKw?.let { it * 1000.0 } ?: 1_500_000.0,
            batteryCurrentMin = -v.batteryRegenMax * regenCharge,
            batteryCurrentMax = v.batteryCurrentMax * battery,
        )
    }

    fun kwToHp(kw: Double) = kw * 1.34102

    /**
     * The VESC's exponential throttle curve (`throttle_exp`): throttle position 0..1 to output 0..1.
     * Negative [exp] is softer at first, positive has more bite. ponytail: the VESC's default exponential
     * mode only; the polynomial and natural modes bend differently.
     */
    fun throttleCurve(x: Double, exp: Double): Double {
        val a = x.coerceIn(0.0, 1.0)
        return if (exp >= 0) 1 - (1 - a).pow(1 + exp) else a.pow(1 - exp)
    }

    /** The [throttleCurve] exponent whose output at half throttle is [mid]: where the curve editor's handle sits. */
    fun throttleExpForMid(mid: Double): Double {
        val m = mid.coerceIn(0.01, 0.99)
        return if (m >= 0.5) -ln(1 - m) / ln(2.0) - 1 else ln(m) / ln(2.0) + 1
    }
}

/** Round up to a "nice" chart bound: 1, 1.2, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10 × 10^n. */
fun niceCeil(x: Double): Double {
    if (x <= 0 || x.isNaN() || x.isInfinite()) return 1.0
    val mag = 10.0.pow(floor(log10(x)))
    val n = x / mag
    val step = listOf(1.0, 1.2, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0).first { n <= it + 1e-9 }
    return step * mag
}

/** Gridline step: 1, 2, 2.5 or 5 × 10^n. */
fun niceStep(x: Double): Double {
    if (x <= 0 || x.isNaN() || x.isInfinite()) return 1.0
    val mag = 10.0.pow(floor(log10(x)))
    val n = x / mag
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { n <= it + 1e-9 }
    return step * mag
}
