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

/** The advanced settings for [m]; each change is handed to [set] as the updated mode. Both mode editors render this list. */
internal fun advancedFields(m: DriveMode, v: VehicleSettings, set: (DriveMode) -> Unit): List<AdvancedField> {
    fun text(on: Boolean, s: String) = if (on) s else "AUTO"
    fun pct(x: Float) = (x / 5f).roundToInt() * 5
    fun snap(x: Float, step: Float) = ((x / step).roundToInt() * step * 100).roundToInt() / 100.0

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
                    "Throttle curve", "Negative is softer, positive has more bite. AUTO uses your base.",
                    m.throttleExp != null, text(m.throttleExp != null, "%.1f".format(m.throttleExp ?: 0.0)),
                    (m.throttleExp ?: v.throttleExp).toFloat().coerceIn(-2f, 2f), -2f, 2f, 0.1f,
                    { on -> set(m.copy(throttleExp = if (on) v.throttleExp.coerceIn(-2.0, 2.0) else null)) },
                ) { set(m.copy(throttleExp = snap(it, 0.1f))) },
            )
            add(
                AdvancedField(
                    "Accel ramp", "Seconds to build throttle. AUTO uses your base.",
                    m.rampUpS != null, text(m.rampUpS != null, "%.2f s".format(m.rampUpS ?: 0.0)),
                    (m.rampUpS ?: v.rampUpS).toFloat().coerceIn(0.05f, 1.5f), 0.05f, 1.5f, 0.05f,
                    { on -> set(m.copy(rampUpS = if (on) v.rampUpS.coerceIn(0.05, 1.5) else null)) },
                ) { set(m.copy(rampUpS = snap(it, 0.05f))) },
            )
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
