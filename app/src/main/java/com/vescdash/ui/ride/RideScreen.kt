package com.vescdash.ui.ride

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vescdash.data.RideStyle
import com.vescdash.ui.MainViewModel

/** Full-screen landscape ride display in the style and theme chosen in Setup. */
@Composable
fun RideScreen(vm: MainViewModel, onHome: () -> Unit) {
    val vehicle by vm.vehicle.collectAsStateWithLifecycle()
    val data = collectRideData(vm)
    val palette = ridePalette(vehicle.rideTheme)
    val cycleMode = {
        val modes = vm.modes.value
        if (modes.size > 1) {
            val i = modes.indexOfFirst { it.id == vm.activeModeId.value }.coerceAtLeast(0)
            vm.selectMode(modes[(i + 1) % modes.size].id)
        }
    }

    ImmersiveMode()

    Crossfade(targetState = vehicle.rideStyle to palette, animationSpec = tween(450), label = "rideStyle") { (style, c) ->
        when (style) {
            RideStyle.CLASSIC -> ClassicRide(data, c, cycleMode, onHome)
            RideStyle.MINIMAL -> MinimalRide(data, c, cycleMode, onHome)
        }
    }
}
