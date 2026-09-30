package com.vescdash.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class Corner(val right: Boolean, val bottom: Boolean) {
    TOP_LEFT(false, false),
    TOP_RIGHT(true, false),
    BOTTOM_LEFT(false, true),
    BOTTOM_RIGHT(true, true),
}

/** Guide lines as screen fractions: x positions of vertical lines, y positions of horizontal ones. */
class Guides(val xs: List<Float>, val ys: List<Float>) {
    companion object {
        val None = Guides(emptyList(), emptyList())
    }
}

/** A rect after snapping, with the guide lines it landed on. */
class Snapped(val rect: RideRect, val guides: Guides)

/**
 * The maths behind the ride-layout editor: keeping a rect on screen and big enough, resizing it
 * and snapping it to guide lines. Rects are screen fractions; sizes and snap distances are in dp,
 * converted with the screen's [widthDp] × [heightDp] so they feel the same on every phone.
 */
class LayoutGeometry(private val widthDp: Float, private val heightDp: Float) {
    /** Smallest allowed side in dp. A gauge needs more room than the rest to stay legible. */
    fun minSizeDp(type: WidgetType?): Float = if (type == WidgetType.GAUGE) GAUGE_MIN_DP else MIN_DP

    /** A sensible starting rect for a new widget, centred on screen. */
    fun defaultRect(type: WidgetType): RideRect {
        val (w, h) = when (type) {
            WidgetType.NUMBER -> 160f to 90f
            WidgetType.GAUGE -> 200f to 200f
            WidgetType.BAR -> 240f to 64f
            WidgetType.GRAPH -> 320f to 110f
        }
        val fw = w / widthDp
        val fh = h / heightDp
        return clamp(RideRect((1f - fw) / 2f, (1f - fh) / 2f, fw, fh), minSizeDp(type))
    }

    /** [r] grown to at least [minDp] on both sides, then moved fully on screen. */
    fun clamp(r: RideRect, minDp: Float): RideRect {
        val w = r.w.coerceAtLeast(minDp / widthDp).coerceAtMost(1f)
        val h = r.h.coerceAtLeast(minDp / heightDp).coerceAtMost(1f)
        return RideRect(r.x.coerceIn(0f, 1f - w), r.y.coerceIn(0f, 1f - h), w, h)
    }

    fun moved(r: RideRect, dx: Float, dy: Float): RideRect = clamp(r.copy(x = r.x + dx, y = r.y + dy), 0f)

    /** [r] offset [dp] down and right for a duplicate, or up and left if it's already against that edge. */
    fun nudged(r: RideRect, dp: Float): RideRect {
        val down = moved(r, dp / widthDp, dp / heightDp)
        return if (down != r) down else moved(r, -dp / widthDp, -dp / heightDp)
    }

    /** [r] scaled by [factor] about its centre, kept on screen and above [minDp]. */
    fun scaled(r: RideRect, factor: Float, minDp: Float): RideRect {
        val lo = max(minDp / widthDp / r.w, minDp / heightDp / r.h)
        val f = factor.coerceAtLeast(lo).coerceAtMost(min(1f / r.w, 1f / r.h))
        val w = r.w * f
        val h = r.h * f
        return clamp(RideRect(r.x + (r.w - w) / 2f, r.y + (r.h - h) / 2f, w, h), minDp)
    }

    /** [corner] of [r] dragged by ([dx], [dy]) while the opposite corner stays put. */
    fun dragCorner(r: RideRect, corner: Corner, dx: Float, dy: Float, minDp: Float): RideRect {
        val minW = minDp / widthDp
        val minH = minDp / heightDp
        var left = r.x
        var top = r.y
        var right = r.right
        var bottom = r.bottom
        if (corner.right) right = (right + dx).coerceAtMost(1f).coerceAtLeast(left + minW)
        else left = (left + dx).coerceAtLeast(0f).coerceAtMost(right - minW)
        if (corner.bottom) bottom = (bottom + dy).coerceAtMost(1f).coerceAtLeast(top + minH)
        else top = (top + dy).coerceAtLeast(0f).coerceAtMost(bottom - minH)
        return clamp(RideRect(left, top, right - left, bottom - top), minDp)
    }

    /** The screen's edges and centre lines plus the edges and centres of [others]. */
    fun guideLines(others: List<RideRect>) = Guides(
        xs = listOf(0f, 0.5f, 1f) + others.flatMap { listOf(it.x, it.x + it.w / 2f, it.right) },
        ys = listOf(0f, 0.5f, 1f) + others.flatMap { listOf(it.y, it.y + it.h / 2f, it.bottom) },
    )

    /** [r] shifted so an edge or centre lands on a line, if one is within snap range. */
    fun snapMove(r: RideRect, lines: Guides): Snapped {
        val sx = snapAxis(listOf(r.x, r.x + r.w / 2f, r.right), lines.xs, SNAP_DP / widthDp)
        val sy = snapAxis(listOf(r.y, r.y + r.h / 2f, r.bottom), lines.ys, SNAP_DP / heightDp)
        return Snapped(clamp(r.copy(x = r.x + sx.offset, y = r.y + sy.offset), 0f), Guides(sx.fired, sy.fired))
    }

    /** [r] with the two edges meeting at [corner] snapped to a line, if one is within snap range. */
    fun snapCorner(r: RideRect, corner: Corner, lines: Guides, minDp: Float): Snapped {
        val sx = snapAxis(listOf(if (corner.right) r.right else r.x), lines.xs, SNAP_DP / widthDp)
        val sy = snapAxis(listOf(if (corner.bottom) r.bottom else r.y), lines.ys, SNAP_DP / heightDp)
        return Snapped(dragCorner(r, corner, sx.offset, sy.offset, minDp), Guides(sx.fired, sy.fired))
    }

    private class Axis(val offset: Float, val fired: List<Float>)

    /** The smallest shift that puts a [candidates] value on a line within [threshold], and every line it then sits on. */
    private fun snapAxis(candidates: List<Float>, lines: List<Float>, threshold: Float): Axis {
        var best = Float.POSITIVE_INFINITY
        for (c in candidates) for (l in lines) {
            val d = l - c
            if (abs(d) <= threshold && abs(d) < abs(best)) best = d
        }
        if (best.isInfinite()) return Axis(0f, emptyList())
        return Axis(best, lines.filter { l -> candidates.any { abs(it + best - l) < EPS } }.distinct())
    }

    private companion object {
        const val MIN_DP = 48f
        const val GAUGE_MIN_DP = 96f
        const val SNAP_DP = 8f
        const val EPS = 1e-4f
    }
}
