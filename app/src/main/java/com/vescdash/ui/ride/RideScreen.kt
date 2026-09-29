package com.vescdash.ui.ride

import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vescdash.data.RideStyle
import com.vescdash.data.VehicleMath
import com.vescdash.ui.MainViewModel
import kotlinx.coroutines.launch

/** Full-screen landscape ride display in the style and theme chosen in Setup. */
@Composable
fun RideScreen(vm: MainViewModel, onHome: () -> Unit) {
    val vehicle by vm.vehicle.collectAsStateWithLifecycle()
    val modes by vm.modes.collectAsStateWithLifecycle()
    val activeId by vm.activeModeId.collectAsStateWithLifecycle()
    val data = collectRideData(vm)
    val palette = ridePalette(vehicle.rideTheme)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cycleMode = {
        if (modes.size > 1) {
            val i = modes.indexOfFirst { it.id == vm.activeModeId.value }.coerceAtLeast(0)
            vm.selectMode(modes[(i + 1) % modes.size].id)
        }
    }

    // Alpha mode = the mode with the highest estimated peak power (as long as not all modes tie).
    val effectiveId = modes.firstOrNull { it.id == activeId }?.id ?: modes.firstOrNull()?.id
    val peaks = remember(modes, vehicle) { modes.associate { it.id to VehicleMath.peakKw(it, vehicle) } }
    val cinematic = remember { Animatable(1f) }
    var cinematicInfo by remember { mutableStateOf<CinematicInfo?>(null) }
    var lastModeId by remember { mutableStateOf(effectiveId) }
    val shownAt = remember { SystemClock.elapsedRealtime() }

    LaunchedEffect(effectiveId) {
        val previous = lastModeId
        lastModeId = effectiveId
        // Only on a real switch while riding, not when settings load as the screen opens.
        if (previous == null || effectiveId == null || previous == effectiveId) return@LaunchedEffect
        if (SystemClock.elapsedRealtime() - shownAt < 1_000) return@LaunchedEffect
        val top = peaks.values.maxOrNull() ?: return@LaunchedEffect
        val mine = peaks[effectiveId] ?: return@LaunchedEffect
        val isAlpha = mine >= top - 1e-6 && peaks.values.any { it < top - 1e-6 }
        if (!isAlpha) return@LaunchedEffect
        val index = modes.indexOfFirst { it.id == effectiveId }
        cinematicInfo = CinematicInfo(index + 1, Color(modes[index].color), mine)
        playAlphaHaptics(context)
        cinematic.snapTo(0f)
        cinematic.animateTo(1f, tween(ALPHA_CINEMATIC_MS, easing = LinearEasing))
    }

    ImmersiveMode()

    val progress = cinematic.value
    val info = cinematicInfo
    val playing = info != null && progress < 1f

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (playing) {
                        val shake = cinematicShake(progress, 0.025f * size.height)
                        translationX = shake.x
                        translationY = shake.y
                        val punch = cinematicPunch(progress)
                        scaleX = punch
                        scaleY = punch
                    }
                },
        ) {
            Crossfade(targetState = vehicle.rideStyle to palette, animationSpec = tween(450), label = "rideStyle") { (style, c) ->
                when (style) {
                    RideStyle.CLASSIC -> ClassicRide(data, c, cycleMode, onHome)
                    RideStyle.MINIMAL -> MinimalRide(data, c, cycleMode, onHome)
                }
            }
        }
        if (playing) {
            // Tap anywhere to skip.
            AlphaCinematic(
                progress,
                info,
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        scope.launch { cinematic.snapTo(1f) }
                    },
            )
        }
    }
}
