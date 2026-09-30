package com.vescdash.ui.ride

/** Torque that starts a launch from (nearly) standing, below [LAUNCH_LOW_SPEED_KMH]; the least any launch starts on. */
internal const val LAUNCH_MIN_TORQUE = 0.6f
private const val LAUNCH_LOW_SPEED_KMH = 8.0

/** Torque that starts a launch at any speed up to [LAUNCH_MAX_SPEED_KMH]. */
private const val LAUNCH_HARD_TORQUE = 0.85f
private const val LAUNCH_MAX_SPEED_KMH = 50.0

/** A launch ends when torque falls below this (regen included). Well under the start threshold, so it doesn't flicker. */
private const val LAUNCH_END_TORQUE = 0.35f
private const val RAMP_IN_S = 0.15f
private const val FADE_OUT_S = 0.5f
private const val FOLLOW_FALL_S = 0.4f
private const val COOLDOWN_S = 2f

/**
 * Decides when the rider is launching hard and how strongly to show it. Feed it the speed and
 * the torque fraction ([RideData.torque]) every frame; [intensity] (0..1) ramps in quickly,
 * follows the throttle while the launch lasts and fades out after it, then a cooldown holds
 * off the next launch. No link means no animation.
 */
internal class LaunchDetector {
    var intensity = 0f
        private set
    var active = false
        private set
    private var cooldown = 0f

    /** Nothing showing and nothing pending: the caller can stop stepping until the throttle rises. */
    val settled: Boolean get() = !active && intensity == 0f && cooldown == 0f

    fun update(dt: Float, speedKmh: Double, torque: Float, linked: Boolean) {
        cooldown = (cooldown - dt).coerceAtLeast(0f)
        if (!active) {
            if (linked && cooldown == 0f && startsLaunch(speedKmh, torque)) active = true
        } else if (!linked || torque < LAUNCH_END_TORQUE) {
            active = false
            cooldown = COOLDOWN_S
        }
        intensity = if (active) {
            val step = torque.coerceIn(0f, 1f) - intensity
            intensity + step.coerceIn(-dt / FOLLOW_FALL_S, dt / RAMP_IN_S)
        } else {
            (intensity - dt / FADE_OUT_S).coerceAtLeast(0f)
        }
    }

    private fun startsLaunch(speedKmh: Double, torque: Float) =
        (torque > LAUNCH_MIN_TORQUE && speedKmh < LAUNCH_LOW_SPEED_KMH) ||
            (torque > LAUNCH_HARD_TORQUE && speedKmh < LAUNCH_MAX_SPEED_KMH)
}
