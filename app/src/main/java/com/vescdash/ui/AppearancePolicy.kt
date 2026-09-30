package com.vescdash.ui

import android.content.pm.ActivityInfo
import com.vescdash.data.AppAppearance

/** Stark Varg is landscape-only; Classic follows the phone (its ride view is the landscape half). */
internal fun orientationFor(appearance: AppAppearance): Int = when (appearance) {
    AppAppearance.CLASSIC -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    AppAppearance.STARK -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
}

/**
 * Whether the full-screen ride view is showing. Classic: turning the phone sideways, unless the
 * home button was pressed ([homeInLandscape]). Stark can't tell a ride from the app by how the phone
 * is held, so it shows the ride view only after riding off or pressing the ride button ([rideOpen]).
 */
internal fun showRideView(appearance: AppAppearance, landscape: Boolean, homeInLandscape: Boolean, rideOpen: Boolean): Boolean =
    when (appearance) {
        AppAppearance.CLASSIC -> landscape && !homeInLandscape
        AppAppearance.STARK -> rideOpen
    }
