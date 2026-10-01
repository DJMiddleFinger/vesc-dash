package com.vescdash.data

import com.vescdash.data.AccelRun.State
import org.junit.Assert.assertEquals
import org.junit.Test

class AccelRunTest {
    /** Feeds (ms, speed) samples to a run armed for [targets]. */
    private fun run(targets: List<Double>, vararg samples: Pair<Long, Double>) =
        samples.fold(AccelRun.armed("w", targets)) { r, (ms, speed) -> r.step(ms, speed) }

    @Test
    fun startAndSplitsAreInterpolatedBetweenSamples() {
        // Passes 1 at 150 ms, 15 at 330 ms and 30 at 480 ms: splits of 180 and 330 ms.
        val r = run(listOf(15.0, 30.0), 0L to 0.0, 100L to 0.0, 200L to 2.0, 300L to 12.0, 400L to 22.0, 500L to 32.0)
        assertEquals(State.DONE, r.state)
        assertEquals(listOf(180.0, 330.0), r.splitsMs)
    }

    @Test
    fun armingWhileRollingWaitsForAStopAndAFreshLaunch() {
        val rolling = run(listOf(15.0), 0L to 5.0, 100L to 6.0, 200L to 7.0)
        assertEquals(State.ARMED, rolling.state)
        val r = listOf(300L to 0.0, 400L to 0.0, 500L to 20.0).fold(rolling) { acc, (ms, s) -> acc.step(ms, s) }
        assertEquals(State.DONE, r.state)
    }

    @Test
    fun stoppingBeforeTheTopTargetKeepsTheSplitsEarned() {
        val r = run(listOf(10.0, 30.0), 0L to 0.0, 100L to 0.0, 200L to 12.0, 300L to 0.5)
        assertEquals(State.DONE, r.state)
        assertEquals(1, r.splitsMs.size)
    }

    @Test
    fun aDropoutMidRunEndsItButNotWhileStillArmed() {
        val running = run(listOf(30.0), 0L to 0.0, 100L to 0.0, 200L to 5.0)
        assertEquals(State.RUNNING, running.state)
        assertEquals(State.DONE, running.step(5_000L, 40.0).state)
        assertEquals(State.ARMED, run(listOf(30.0), 0L to 0.0, 5_000L to 40.0).state)
    }

    @Test
    fun targetsAreSortedAboveTheStartSpeedAndDefaultTo30() {
        val w = DashWidget("a", WidgetType.ACCEL, Metric.SPEED, targets = listOf(60.0, 0.5, 30.0, 60.0))
        assertEquals(listOf(30.0, 60.0), w.accelTargets())
        assertEquals(listOf(30.0), w.copy(targets = emptyList()).accelTargets())
    }
}
