package com.vescdash.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BatteryEstimatorTest {
    private val cells = 13
    private val packOhms = 0.06

    /** Ride that alternates 60 A pulls with coasting while the pack slowly drains. */
    private fun ride(capacityAh: Double): Triple<List<Double>, List<Double>, List<Double>> {
        val est = BatteryEstimator()
        val raw = mutableListOf<Double>()
        val shown = mutableListOf<Double>()
        val truth = mutableListOf<Double>()
        var ah = 0.0
        val startOcvPerCell = 3.95
        for (i in 0 until 3000) { // 300 s at 10 Hz
            val t = i * 100L
            val current = if ((i / 50) % 2 == 0) 60.0 else 0.5
            ah += current * 0.1 / 3600.0
            val ocvPerCell = startOcvPerCell - 0.08 * i / 3000.0
            val voltage = ocvPerCell * cells - current * packOhms
            raw += Battery.percent(voltage / cells)
            truth += Battery.percent(ocvPerCell)
            shown += est.update(voltage, current, ah, t, cells, capacityAh)
        }
        return Triple(raw, shown, truth)
    }

    @Test
    fun rawVoltageSagsButStabilizedReadingDoesNot() {
        val (raw, shown, _) = ride(capacityAh = 0.0)
        val rawSwing = raw.zipWithNext { a, b -> abs(a - b) }.max()
        assertTrue("raw reading should jump under load, was $rawSwing", rawSwing > 5.0)

        val shownSwing = shown.zipWithNext { a, b -> abs(a - b) }.max()
        assertTrue("stabilized reading should move smoothly, max step $shownSwing", shownSwing <= 0.2 + 1e-9)
    }

    @Test
    fun stabilizedReadingNeverRisesUnderLoadAndTracksTruth() {
        val (_, shown, truth) = ride(capacityAh = 0.0)
        // After warm-up, within a few percent of the true state of charge.
        for (i in 600 until shown.size step 100) {
            assertEquals("sample $i", truth[i], shown[i], 4.0)
        }
        // Once settled it drains and never climbs noticeably.
        val rises = shown.drop(600).zipWithNext { a, b -> b - a }.filter { it > 0.05 }
        assertTrue("should not climb, rises=$rises", rises.isEmpty())
        assertTrue(shown.last() < shown[600])
    }

    @Test
    fun learnsPackResistance() {
        val est = BatteryEstimator()
        var ah = 0.0
        for (i in 0 until 600) {
            val current = if ((i / 20) % 2 == 0) 40.0 else 0.0
            ah += current * 0.1 / 3600.0
            est.update(3.9 * cells - current * packOhms, current, ah, i * 100L, cells, 0.0)
        }
        assertEquals(packOhms, est.resistanceOhm, 0.01)
    }

    @Test
    fun coulombCountingFollowsChargeUsed() {
        val est = BatteryEstimator()
        // Resting at 3.9 V/cell long enough to anchor, then draw 2 Ah from a 20 Ah pack.
        var t = 0L
        repeat(50) { est.update(3.9 * cells, 0.0, 0.0, t, cells, 20.0); t += 100 }
        val start = est.update(3.9 * cells, 0.0, 0.0, t, cells, 20.0)
        var ah = 0.0
        var shown = start
        repeat(3600) { // 6 min at 20 A = 2 Ah, voltage sagging hard throughout
            t += 100
            ah += 20.0 * 0.1 / 3600.0
            shown = est.update(3.9 * cells - 20.0 * 0.2, 20.0, ah, t, cells, 20.0)
        }
        assertEquals(start - 10.0, shown, 2.5)
    }
}
