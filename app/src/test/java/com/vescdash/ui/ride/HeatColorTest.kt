package com.vescdash.ui.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatColorTest {
    private val c = RidePalette.Dark

    @Test
    fun startsYellowAndEndsRed() {
        assertEquals(c.heatStart, heatColor(60.0, 60.0, 100.0, c))
        assertEquals(c.red, heatColor(100.0, 60.0, 100.0, c))
        assertEquals(c.red, heatColor(120.0, 60.0, 100.0, c))
    }

    @Test
    fun turnsRedEarly() {
        // A quarter of the way to the danger point, most of the yellow's green is already gone.
        val quarter = heatColor(70.0, 60.0, 100.0, c)
        val greenLeft = (quarter.green - c.red.green) / (c.heatStart.green - c.red.green)
        assertTrue("green left at 25%: $greenLeft", greenLeft < 0.35f)
    }

    @Test
    fun startAtOrPastRedIsRedImmediately() {
        assertEquals(c.red, heatColor(90.0, 95.0, 85.0, c))
    }
}
