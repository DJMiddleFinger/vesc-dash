package com.vescdash.ui.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchDetectorTest {
    private val dt = 0.05f

    /** Steps [seconds] of constant input and returns the peak intensity seen. */
    private fun LaunchDetector.run(seconds: Float, speed: Double, torque: Float, linked: Boolean = true): Float {
        var peak = 0f
        repeat((seconds / dt).toInt()) {
            update(dt, speed, torque, linked)
            peak = maxOf(peak, intensity)
        }
        return peak
    }

    @Test
    fun hardPullFromStandstillStartsAndRampsInQuickly() {
        val d = LaunchDetector()
        d.run(0.2f, speed = 2.0, torque = 0.9f)
        assertTrue(d.active)
        assertEquals(0.9f, d.intensity, 1e-3f)
    }

    @Test
    fun gentlePullOrMidSpeedPullDoesNotStart() {
        val d = LaunchDetector()
        assertEquals(0f, d.run(2f, speed = 2.0, torque = 0.5f), 0f)
        // 0.7 is enough from a standstill but not at 20 km/h; 0.9 is.
        assertEquals(0f, d.run(2f, speed = 20.0, torque = 0.7f), 0f)
        d.run(0.2f, speed = 20.0, torque = 0.9f)
        assertTrue(d.active)
    }

    @Test
    fun veryHighSpeedNeverStarts() {
        val d = LaunchDetector()
        assertEquals(0f, d.run(2f, speed = 70.0, torque = 1f), 0f)
    }

    @Test
    fun staysOnUntilTorqueDropsWellBelowTheStartThreshold() {
        val d = LaunchDetector()
        d.run(0.5f, speed = 2.0, torque = 0.9f)
        d.run(2f, speed = 15.0, torque = 0.4f)
        assertTrue("0.4 is between the thresholds, so it holds", d.active)
        d.run(0.1f, speed = 15.0, torque = 0.3f)
        assertFalse(d.active)
    }

    @Test
    fun followsTheThrottleWhileActive() {
        val d = LaunchDetector()
        d.run(0.5f, speed = 2.0, torque = 1f)
        assertEquals(1f, d.intensity, 1e-3f)
        d.run(1f, speed = 10.0, torque = 0.6f)
        assertEquals(0.6f, d.intensity, 1e-3f)
    }

    @Test
    fun fadesOutOverHalfASecondAfterLettingOff() {
        val d = LaunchDetector()
        d.run(0.5f, speed = 2.0, torque = 1f)
        d.run(0.25f, speed = 10.0, torque = 0f)
        assertTrue("halfway through the fade", d.intensity in 0.3f..0.7f)
        d.run(0.3f, speed = 10.0, torque = 0f)
        assertEquals(0f, d.intensity, 0f)
    }

    @Test
    fun regenEndsTheLaunch() {
        val d = LaunchDetector()
        d.run(0.5f, speed = 2.0, torque = 1f)
        d.run(0.1f, speed = 10.0, torque = -0.5f)
        assertFalse(d.active)
    }

    @Test
    fun cooldownHoldsOffTheNextLaunch() {
        val d = LaunchDetector()
        d.run(0.5f, speed = 2.0, torque = 1f)
        d.run(1f, speed = 2.0, torque = 0f)
        assertEquals("still cooling down", 0f, d.run(0.5f, speed = 2.0, torque = 1f), 0f)
        d.run(0.5f, speed = 2.0, torque = 0f)
        d.run(0.3f, speed = 2.0, torque = 1f)
        assertTrue("cooldown is over", d.active)
    }

    @Test
    fun noLinkMeansNoAnimation() {
        val d = LaunchDetector()
        assertEquals(0f, d.run(1f, speed = 2.0, torque = 1f, linked = false), 0f)
        d.run(0.5f, speed = 2.0, torque = 1f)
        assertTrue(d.active)
        d.run(0.6f, speed = 2.0, torque = 1f, linked = false)
        assertFalse(d.active)
        assertEquals(0f, d.intensity, 0f)
    }

    @Test
    fun settlesOnceEverythingIsQuiet() {
        val d = LaunchDetector()
        assertTrue(d.settled)
        d.run(0.5f, speed = 2.0, torque = 1f)
        assertFalse(d.settled)
        d.run(3f, speed = 2.0, torque = 0f)
        assertTrue(d.settled)
    }
}
