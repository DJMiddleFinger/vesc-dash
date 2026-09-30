package com.vescdash.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RideLayoutTest {
    // A 800 × 400 dp screen: 8 dp = 0.01 wide, 0.02 tall; 48 dp = 0.06 wide, 0.12 tall.
    private val geo = LayoutGeometry(800f, 400f)
    private val delta = 1e-5f

    private fun assertRect(expected: RideRect, actual: RideRect) {
        assertEquals("x", expected.x, actual.x, delta)
        assertEquals("y", expected.y, actual.y, delta)
        assertEquals("w", expected.w, actual.w, delta)
        assertEquals("h", expected.h, actual.h, delta)
    }

    @Test
    fun clampKeepsRectOnScreenAndBigEnough() {
        assertRect(RideRect(0.5f, 0.6f, 0.5f, 0.4f), geo.clamp(RideRect(0.8f, 0.9f, 0.5f, 0.4f), 48f))
        assertRect(RideRect(0f, 0f, 0.5f, 0.4f), geo.clamp(RideRect(-0.2f, -0.1f, 0.5f, 0.4f), 48f))
        assertRect(RideRect(0.5f, 0.5f, 0.06f, 0.12f), geo.clamp(RideRect(0.5f, 0.5f, 0.01f, 0.01f), 48f))
        assertRect(RideRect(0f, 0f, 1f, 1f), geo.clamp(RideRect(0.2f, 0.2f, 3f, 3f), 48f))
    }

    @Test
    fun gaugesNeedMoreRoomThanOtherWidgets() {
        assertEquals(48f, geo.minSizeDp(WidgetType.BAR), 0f)
        assertEquals(48f, geo.minSizeDp(null), 0f)
        assertTrue(geo.minSizeDp(WidgetType.GAUGE) > geo.minSizeDp(WidgetType.NUMBER))
    }

    @Test
    fun scalingKeepsTheCentre() {
        val r = RideRect(0.4f, 0.4f, 0.2f, 0.2f)
        val bigger = geo.scaled(r, 1.5f, 48f)
        assertRect(RideRect(0.35f, 0.35f, 0.3f, 0.3f), bigger)
        assertEquals(0.5f, bigger.x + bigger.w / 2f, delta)
        assertEquals(0.5f, bigger.y + bigger.h / 2f, delta)
    }

    @Test
    fun scalingStopsAtMinimumAndAtTheScreenEdge() {
        val tiny = geo.scaled(RideRect(0.4f, 0.4f, 0.2f, 0.2f), 0.01f, 48f)
        assertTrue("w ${tiny.w} h ${tiny.h}", tiny.w >= 0.06f - delta && tiny.h >= 0.12f - delta)
        // Growing past the screen slides the rect back on rather than pushing it off.
        val huge = geo.scaled(RideRect(0.7f, 0.1f, 0.3f, 0.3f), 10f, 48f)
        assertTrue(huge.x >= 0f && huge.y >= 0f && huge.right <= 1f + delta && huge.bottom <= 1f + delta)
    }

    @Test
    fun cornerDragMovesOnlyThatCorner() {
        val r = RideRect(0.2f, 0.2f, 0.4f, 0.4f)
        assertRect(RideRect(0.2f, 0.2f, 0.5f, 0.3f), geo.dragCorner(r, Corner.BOTTOM_RIGHT, 0.1f, -0.1f, 48f))
        assertRect(RideRect(0.1f, 0.3f, 0.5f, 0.3f), geo.dragCorner(r, Corner.TOP_LEFT, -0.1f, 0.1f, 48f))
    }

    @Test
    fun cornerDragCannotShrinkPastMinimumOrLeaveTheScreen() {
        val r = RideRect(0.2f, 0.2f, 0.4f, 0.4f)
        // Dragging the bottom-right corner up and left past the top-left one stops at the minimum size.
        assertRect(RideRect(0.2f, 0.2f, 0.06f, 0.12f), geo.dragCorner(r, Corner.BOTTOM_RIGHT, -1f, -1f, 48f))
        assertRect(RideRect(0.2f, 0.2f, 0.8f, 0.8f), geo.dragCorner(r, Corner.BOTTOM_RIGHT, 1f, 1f, 48f))
    }

    @Test
    fun movingSnapsToScreenCentreAndReportsTheGuide() {
        // Centre is 0.005 right of the middle line, inside the 0.01 (8 dp) snap range.
        val snapped = geo.snapMove(RideRect(0.405f, 0.1f, 0.2f, 0.2f), geo.guideLines(emptyList()))
        assertRect(RideRect(0.4f, 0.1f, 0.2f, 0.2f), snapped.rect)
        assertEquals(listOf(0.5f), snapped.guides.xs)
        assertTrue(snapped.guides.ys.isEmpty())
    }

    @Test
    fun movingSnapsToAnotherItemsEdgesOnBothAxes() {
        val other = RideRect(0.1f, 0.5f, 0.2f, 0.2f)
        val lines = geo.guideLines(listOf(other))
        // Left edge is 0.005 off the other's left edge; top is 0.015 (6 dp) below the other's bottom (0.7).
        val snapped = geo.snapMove(RideRect(0.105f, 0.715f, 0.3f, 0.1f), lines)
        assertEquals(0.1f, snapped.rect.x, delta)
        assertEquals(0.7f, snapped.rect.y, delta)
        assertTrue(0.1f in snapped.guides.xs)
        assertTrue(0.7f in snapped.guides.ys)
    }

    @Test
    fun movingOutsideSnapRangeStaysPut() {
        val r = RideRect(0.42f, 0.13f, 0.1f, 0.1f)
        val snapped = geo.snapMove(r, geo.guideLines(emptyList()))
        assertRect(r, snapped.rect)
        assertTrue(snapped.guides.xs.isEmpty() && snapped.guides.ys.isEmpty())
    }

    @Test
    fun snapPicksTheClosestOfSeveralCandidates() {
        // The centre is 0.008 off the middle line but the right edge only 0.002 off another item's: the edge wins.
        val lines = geo.guideLines(listOf(RideRect(0.5f, 0.6f, 0.21f, 0.1f)))
        val snapped = geo.snapMove(RideRect(0.308f, 0.3f, 0.4f, 0.2f), lines)
        assertEquals(0.31f, snapped.rect.x, delta)
        assertEquals(0.71f, snapped.guides.xs.single(), delta)
    }

    @Test
    fun cornerSnapMovesJustThoseEdges() {
        val r = RideRect(0.2f, 0.2f, 0.295f, 0.29f)
        val snapped = geo.snapCorner(r, Corner.BOTTOM_RIGHT, geo.guideLines(emptyList()), 48f)
        assertRect(RideRect(0.2f, 0.2f, 0.3f, 0.3f), snapped.rect)
        assertEquals(listOf(0.5f), snapped.guides.xs)
        assertEquals(listOf(0.5f), snapped.guides.ys)
    }

    @Test
    fun duplicateOffsetFlipsAtTheEdge() {
        assertRect(RideRect(0.12f, 0.24f, 0.2f, 0.2f), geo.nudged(RideRect(0.1f, 0.2f, 0.2f, 0.2f), 16f))
        assertRect(RideRect(0.73f, 0.71f, 0.25f, 0.25f), geo.nudged(RideRect(0.75f, 0.75f, 0.25f, 0.25f), 16f))
    }

    @Test
    fun newWidgetsStartCentredAndNoSmallerThanTheirMinimum() {
        for (type in WidgetType.entries) {
            val r = geo.defaultRect(type)
            assertEquals(type.name, 0.5f, r.x + r.w / 2f, delta)
            assertEquals(type.name, 0.5f, r.y + r.h / 2f, delta)
            assertTrue(type.name, r.w * 800f >= geo.minSizeDp(type) - 0.01f && r.h * 400f >= geo.minSizeDp(type) - 0.01f)
        }
    }

    @Test
    fun layoutSurvivesAJsonRoundTrip() {
        val layout = Defaults.rideLayout.copy(
            items = Defaults.rideLayout.items + RideItem(
                DashWidget("x", WidgetType.GAUGE, Metric.POWER, min = 0.0, max = 12.5, warn = 8.0, danger = 11.0, label = "Kilowatts"),
                RideRect(0.25f, 0.5f, 0.125f, 0.375f),
            ),
        )
        val json = settingsJson.encodeToString(layout)
        assertEquals(layout, settingsJson.decodeFromString<RideLayout>(json))
    }

    @Test
    fun storedLayoutFromANewerVersionStillDecodes() {
        val json = settingsJson.encodeToString(Defaults.rideLayout).replaceFirst("\"modePill\"", "\"someFutureField\":1,\"modePill\"")
        assertEquals(Defaults.rideLayout, settingsJson.decodeFromString<RideLayout>(json))
    }

    @Test
    fun defaultLayoutFitsOnScreen() {
        val all = Defaults.rideLayout.items.map { it.rect } + Defaults.rideLayout.modePill + Defaults.rideLayout.warnings
        for (r in all) assertTrue("$r", r.x >= 0f && r.y >= 0f && r.right <= 1f && r.bottom <= 1f)
    }
}
