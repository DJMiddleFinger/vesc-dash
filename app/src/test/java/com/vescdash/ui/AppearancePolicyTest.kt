package com.vescdash.ui

import android.content.pm.ActivityInfo
import com.vescdash.data.AppAppearance.CLASSIC
import com.vescdash.data.AppAppearance.STARK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearancePolicyTest {
    @Test
    fun onlyStarkLocksLandscape() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, orientationFor(CLASSIC))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, orientationFor(STARK))
    }

    @Test
    fun classicShowsRideViewWhenLandscapeUnlessHomeWasPressed() {
        assertTrue(showRideView(CLASSIC, landscape = true, homeInLandscape = false, rideOpen = false))
        assertFalse(showRideView(CLASSIC, landscape = true, homeInLandscape = true, rideOpen = false))
        assertFalse(showRideView(CLASSIC, landscape = false, homeInLandscape = false, rideOpen = true))
    }

    @Test
    fun starkShowsRideViewOnlyWhenOpened() {
        assertFalse(showRideView(STARK, landscape = true, homeInLandscape = false, rideOpen = false))
        assertTrue(showRideView(STARK, landscape = true, homeInLandscape = true, rideOpen = true))
    }
}
