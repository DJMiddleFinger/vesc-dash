package com.vescdash.data

import com.vescdash.ui.modes.snapPower
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleMathTest {
    // Defaults: 60 A motor, 30 A battery, 10 A regen, 0.95 duty.
    private val v = VehicleSettings()
    private val mode = DriveMode(name = "T", color = 0, powerPct = 50, regenPct = 40)

    @Test
    fun advancedSettingsFollowPowerAndRegenWhenUnset() {
        val l = VehicleMath.limitsFor(mode, v)
        assertEquals(15.0, l.batteryCurrentMax, 1e-9)
        assertEquals(-4.0, l.batteryCurrentMin, 1e-9)
        assertEquals(0.95, l.dutyMax, 1e-9)
        assertEquals(-1_500_000.0, l.wattMin, 1e-9)
    }

    @Test
    fun advancedOverridesApplyIndependentlyOfPowerAndRegen() {
        val l = VehicleMath.limitsFor(mode.copy(batteryPct = 80, regenChargePct = 20, maxDutyPct = 70, regenCapKw = 2.0), v)
        assertEquals(0.5, l.currentMaxScale, 1e-9)
        assertEquals(0.4, l.currentMinScale, 1e-9)
        assertEquals(24.0, l.batteryCurrentMax, 1e-9)
        assertEquals(-2.0, l.batteryCurrentMin, 1e-9)
        assertEquals(0.7, l.dutyMax, 1e-9)
        assertEquals(-2000.0, l.wattMin, 1e-9)
    }

    @Test
    fun dutyCapNeverExceedsTheBaseAndShortensTheCurve() {
        assertEquals(0.95, VehicleMath.limitsFor(mode.copy(maxDutyPct = 99), v).dutyMax, 1e-9)
        val full = VehicleMath.powerCurve(mode, v).last().kmh
        assertTrue(VehicleMath.powerCurve(mode.copy(maxDutyPct = 50), v).last().kmh < full)
    }

    @Test
    fun throttleCurveIsLinearAtZeroAndTheEditorHandleMapsBack() {
        assertEquals(0.3, VehicleMath.throttleCurve(0.3, 0.0), 1e-9)
        assertTrue(VehicleMath.throttleCurve(0.2, 1.0) > 0.2 && VehicleMath.throttleCurve(0.2, -1.0) < 0.2)
        for (e in listOf(-2.0, -0.7, 0.0, 1.3, 2.0)) {
            assertEquals(e, VehicleMath.throttleExpForMid(VehicleMath.throttleCurve(0.5, e)), 1e-9)
        }
    }

    @Test
    fun powerGoesDownToOnePercentInSteps() {
        assertEquals(0.01, VehicleMath.limitsFor(mode.copy(powerPct = 1), v).currentMaxScale, 1e-9)
        assertEquals(listOf(1, 1, 5, 5, 10, 15, 100), listOf(1f, 2.9f, 5f, 7.4f, 10f, 14f, 100f).map(::snapPower))
    }
}
