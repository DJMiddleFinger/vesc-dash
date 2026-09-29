package com.vescdash.data

import kotlin.math.abs
import kotlin.math.min

/**
 * Turns raw pack voltage into a steady battery percentage.
 *
 * Pack voltage sags under load (V = OCV − I·R), so mapping voltage straight to % makes
 * the reading drop when you accelerate and jump back when you let off. This:
 *
 *  1. learns the pack's internal resistance R from how voltage moves when current steps,
 *  2. adds the sag back (OCV ≈ V + I·R) before converting to %,
 *  3. if the pack capacity is known, counts charge used since the last at-rest reading
 *     (coulomb counting doesn't sag at all); every stop re-anchors it to the resting voltage,
 *  4. smooths the result, never lets it rise while under load, and limits how fast it moves.
 */
class BatteryEstimator {
    /** Learned pack resistance in ohms (NaN until the first sample). */
    var resistanceOhm: Double = Double.NaN
        private set

    private var display = Double.NaN
    private var lastV = Double.NaN
    private var lastI = Double.NaN
    private var lastTimeMs = 0L
    private var restSinceMs = -1L
    private var anchorSoc = Double.NaN
    private var anchorAh = 0.0

    /** Forget the displayed value and rest anchor (keeps the learned resistance). */
    fun reset() {
        display = Double.NaN
        lastV = Double.NaN
        lastI = Double.NaN
        lastTimeMs = 0L
        restSinceMs = -1L
        anchorSoc = Double.NaN
        anchorAh = 0.0
    }

    /**
     * @param current battery current in A, positive = discharging
     * @param ahNet amp-hours drawn minus amp-hours regenerated (VESC counters)
     * @param capacityAh pack capacity, or 0 if unknown
     * @return stabilized state of charge, 0–100
     */
    fun update(
        voltage: Double,
        current: Double,
        ahNet: Double,
        timeMs: Long,
        cells: Int,
        capacityAh: Double,
    ): Double {
        val n = cells.coerceAtLeast(1)
        if (resistanceOhm.isNaN()) resistanceOhm = STARTING_OHMS_PER_CELL * n
        val dt = if (lastTimeMs == 0L) 0.0 else ((timeMs - lastTimeMs) / 1000.0).coerceIn(0.0, 5.0)

        // 1. Learn R from current steps: a sudden change in current moves voltage by −ΔI·R.
        if (!lastI.isNaN()) {
            val dI = current - lastI
            if (abs(dI) > MIN_STEP_AMPS) {
                val r = -(voltage - lastV) / dI
                if (r in MIN_OHMS_PER_CELL * n..MAX_OHMS_PER_CELL * n) {
                    resistanceOhm += (r - resistanceOhm) * R_LEARN_RATE
                }
            }
        }
        lastV = voltage
        lastI = current
        lastTimeMs = timeMs

        // 2. Sag-compensated percentage.
        val socVoltage = Battery.percent((voltage + current * resistanceOhm) / n)

        // 3. Re-anchor while resting; count charge from the anchor when capacity is known.
        val resting = abs(current) < REST_AMPS
        if (!resting) {
            restSinceMs = -1L
        } else if (restSinceMs < 0) {
            restSinceMs = timeMs
        }
        val settled = resting && timeMs - restSinceMs >= REST_SETTLE_MS
        if (anchorSoc.isNaN() || settled) {
            anchorSoc = socVoltage
            anchorAh = ahNet
        }
        val target = if (capacityAh > 0) {
            anchorSoc - (ahNet - anchorAh) / capacityAh * 100.0
        } else {
            socVoltage
        }

        // 4. Smooth, no rising under load, limited slew (faster at rest, where voltage is honest).
        if (display.isNaN()) {
            display = target.coerceIn(0.0, 100.0)
            return display
        }
        var next = display + (target - display) * min(1.0, dt / SMOOTHING_S)
        if (next > display && !resting) next = display
        val maxStep = (if (resting) REST_SLEW_PCT_PER_S else LOAD_SLEW_PCT_PER_S) * dt
        next = next.coerceIn(display - maxStep, display + maxStep)
        display = next.coerceIn(0.0, 100.0)
        return display
    }

    private companion object {
        const val STARTING_OHMS_PER_CELL = 0.0025
        const val MIN_OHMS_PER_CELL = 0.0005
        const val MAX_OHMS_PER_CELL = 0.03
        const val MIN_STEP_AMPS = 3.0
        const val R_LEARN_RATE = 0.1
        const val REST_AMPS = 1.0
        const val REST_SETTLE_MS = 3_000L
        const val SMOOTHING_S = 8.0
        const val LOAD_SLEW_PCT_PER_S = 0.5
        const val REST_SLEW_PCT_PER_S = 2.0
    }
}
