package com.vescdash.data

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
}
