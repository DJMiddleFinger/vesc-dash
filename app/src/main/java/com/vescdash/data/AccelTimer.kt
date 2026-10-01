package com.vescdash.data

/** Speed (display units) the timer starts at, and the one below which the rider counts as stopped. */
const val ACCEL_START = 1.0
private const val DEFAULT_TARGET = 30.0

/** A gap between samples longer than this (a Bluetooth dropout) can't be timed across. */
private const val MAX_GAP_MS = 1_000L

/** The speeds an accel widget times to: above the start speed, ascending, 30 if none are set. */
fun DashWidget.accelTargets(): List<Double> =
    targets.filter { it > ACCEL_START }.distinct().sorted().ifEmpty { listOf(DEFAULT_TARGET) }

/**
 * One standing-start timing run. [armed] at a stop, it starts when the speed rises through [ACCEL_START]
 * and records the time to each of [targets] (ascending, display units); [step] it with every sample.
 * Times between samples are interpolated, so the poll rate doesn't set the resolution.
 */
data class AccelRun(
    /** The widget that armed it; the others stay idle. */
    val id: String = "",
    val targets: List<Double> = emptyList(),
    val state: State = State.IDLE,
    val startMs: Double = 0.0,
    /** The previous sample: time and speed. Its time doubles as "now" for the live reading. */
    val last: Pair<Long, Double>? = null,
    /** Elapsed ms at each target reached so far, in order. */
    val splitsMs: List<Double> = emptyList(),
) {
    enum class State { IDLE, ARMED, RUNNING, DONE }

    fun step(ms: Long, speed: Double): AccelRun {
        if (state == State.IDLE || state == State.DONE) return this
        val (lastMs, lastSpeed) = last ?: return copy(last = ms to speed)
        val now = copy(last = ms to speed)
        if (ms - lastMs > MAX_GAP_MS) return if (state == State.RUNNING) now.copy(state = State.DONE) else now

        var run = now
        if (state == State.ARMED) {
            val start = crossing(lastMs, lastSpeed, ms, speed, ACCEL_START) ?: return now
            run = now.copy(state = State.RUNNING, startMs = start)
        }
        val splits = splitsMs.toMutableList()
        while (splits.size < targets.size) {
            splits += (crossing(lastMs, lastSpeed, ms, speed, targets[splits.size]) ?: break) - run.startMs
        }
        val finished = splits.size == targets.size || speed < ACCEL_START
        return run.copy(splitsMs = splits, state = if (finished) State.DONE else State.RUNNING)
    }

    companion object {
        fun armed(id: String, targets: List<Double>) = AccelRun(id, targets, State.ARMED)
    }
}

/** When a straight line from ([t0], [s0]) to ([t1], [s1]) rises through [level], or null if it doesn't. */
private fun crossing(t0: Long, s0: Double, t1: Long, s1: Double, level: Double): Double? =
    if (s0 < level && s1 >= level) t0 + (t1 - t0) * (level - s0) / (s1 - s0) else null
