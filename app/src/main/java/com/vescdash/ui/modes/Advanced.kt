package com.vescdash.ui.modes

import com.vescdash.data.DriveMode
import com.vescdash.data.VehicleMath
import com.vescdash.data.VehicleSettings
import kotlin.math.roundToInt

/** One advanced setting of a mode: off follows the basic setting (or the Setup base value), on overrides it. */
internal class AdvancedField(
    val title: String,
    val help: String,
    val enabled: Boolean,
    val valueText: String,
    val value: Float,
    val min: Float,
    val max: Float,
    val step: Float,
    val onToggle: (Boolean) -> Unit,
    val onValue: (Float) -> Unit,
)

private fun text(on: Boolean, s: String) = if (on) s else "AUTO"
private fun snap(x: Float, step: Float) = ((x / step).roundToInt() * step * 100).roundToInt() / 100.0

/** Power % steps: 1, 5, then every 5 up to 100. */
internal fun snapPower(x: Float): Int = if (x < 3f) 1 else if (x < 7.5f) 5 else ((x / 5f).roundToInt() * 5).coerceAtMost(100)

/**
 * How long full throttle takes to deliver full power, from instant to slow; sits under Power next to the
 * throttle curve. Null unless Setup lets modes change the throttle.
 */
internal fun powerBuildField(m: DriveMode, v: VehicleSettings, set: (DriveMode) -> Unit): AdvancedField? {
    if (!v.throttleControl) return null
    val on = m.rampUpS != null
    val s = m.rampUpS ?: 0.0
    return AdvancedField(
        "Power build-up", "Instant to slow. AUTO uses your base.",
        on, text(on, if (s < 0.025) "INSTANT" else "%.2f s".format(s)),
        (m.rampUpS ?: v.rampUpS).toFloat().coerceIn(0f, 3f), 0f, 3f, 0.05f,
        { enable -> set(m.copy(rampUpS = if (enable) v.rampUpS.coerceIn(0.0, 3.0) else null)) },
    ) { set(m.copy(rampUpS = snap(it, 0.05f))) }
}

/** The advanced settings for [m]; each change is handed to [set] as the updated mode. Both mode editors render this list. */
internal fun advancedFields(m: DriveMode, v: VehicleSettings, set: (DriveMode) -> Unit): List<AdvancedField> {
    fun pct(x: Float) = (x / 5f).roundToInt() * 5

    val dutyBase = (v.maxDuty * 100).roundToInt().coerceAtLeast(10)
    val dutyLo = (dutyBase / 2).coerceAtLeast(5)
    val fullKw = VehicleMath.peakKw(null, v).coerceAtLeast(2.0).toFloat()

    return buildList {
        add(
            AdvancedField(
                "Battery current", "Battery draw. AUTO follows Power.",
                m.batteryPct != null, text(m.batteryPct != null, "${m.batteryPct}%"),
                (m.batteryPct ?: m.powerPct).toFloat(), 10f, 100f, 5f,
                { on -> set(m.copy(batteryPct = if (on) m.powerPct else null)) },
            ) { set(m.copy(batteryPct = pct(it))) },
        )
        add(
            AdvancedField(
                "Regen charge", "Charge into the battery. AUTO follows Regen.",
                m.regenChargePct != null, text(m.regenChargePct != null, "${m.regenChargePct}%"),
                (m.regenChargePct ?: m.regenPct).toFloat(), 10f, 100f, 5f,
                { on -> set(m.copy(regenChargePct = if (on) m.regenPct else null)) },
            ) { set(m.copy(regenChargePct = pct(it))) },
        )
        add(
            AdvancedField(
                "Max duty", "Softer top end. AUTO uses the base.",
                m.maxDutyPct != null, text(m.maxDutyPct != null, "${m.maxDutyPct}%"),
                (m.maxDutyPct ?: dutyBase).toFloat().coerceIn(dutyLo.toFloat(), dutyBase.toFloat()), dutyLo.toFloat(), dutyBase.toFloat(), 1f,
                { on -> set(m.copy(maxDutyPct = if (on) (dutyBase * 0.9).roundToInt().coerceIn(dutyLo, dutyBase) else null)) },
            ) { set(m.copy(maxDutyPct = it.roundToInt())) },
        )
        add(
            AdvancedField(
                "Regen power cap", "Max charging kW. AUTO is no cap.",
                m.regenCapKw != null, text(m.regenCapKw != null, "${fmtKw(m.regenCapKw ?: 0.0)} kW"),
                (m.regenCapKw ?: fullKw.toDouble()).toFloat().coerceIn(0.5f, fullKw), 0.5f, fullKw, 0.5f,
                { on -> set(m.copy(regenCapKw = if (on) (fullKw * 0.5f).roundToInt().coerceAtLeast(1).toDouble() else null)) },
            ) { set(m.copy(regenCapKw = snap(it, 0.5f))) },
        )
        if (v.throttleControl) {
            add(
                AdvancedField(
                    "Release ramp", "Seconds to fall off. AUTO uses your base.",
                    m.rampDownS != null, text(m.rampDownS != null, "%.2f s".format(m.rampDownS ?: 0.0)),
                    (m.rampDownS ?: v.rampDownS).toFloat().coerceIn(0.05f, 1.5f), 0.05f, 1.5f, 0.05f,
                    { on -> set(m.copy(rampDownS = if (on) v.rampDownS.coerceIn(0.05, 1.5) else null)) },
                ) { set(m.copy(rampDownS = snap(it, 0.05f))) },
            )
        }
    }
}
