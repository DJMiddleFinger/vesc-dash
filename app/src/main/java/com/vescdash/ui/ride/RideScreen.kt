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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vescdash.data.AppAppearance
import com.vescdash.data.KMH_TO_MPH
import com.vescdash.data.RideStyle
import com.vescdash.data.VehicleMath
import com.vescdash.ui.MainViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Full-screen landscape ride display in the style and theme chosen in Setup. */
@Composable
fun RideScreen(vm: MainViewModel, onHome: () -> Unit) {
    val vehicle by vm.vehicle.collectAsStateWithLifecycle()
    val modes by vm.modes.collectAsStateWithLifecycle()
    val activeId by vm.activeModeId.collectAsStateWithLifecycle()
    val data = collectRideData(vm)
    val stark = vehicle.appearance == AppAppearance.STARK
    val themed = ridePalette(vehicle.rideTheme)
    val palette = if (stark) RidePalette.Stark else themed
    val rideStyle = if (stark) RideStyle.CLASSIC else vehicle.rideStyle
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

    // Launch animation: stepped once per frame while a launch is (or could be) under way, and kept in
    // state that is only read while drawing, so a frame never recomposes the ride view.
    val launchIntensity = remember { mutableFloatStateOf(0f) }
    val launchPhase = remember { mutableFloatStateOf(0f) }
    val launching by remember { derivedStateOf { launchIntensity.floatValue > 0f } }
    val latest by rememberUpdatedState(data)
    val launchEnabled = vehicle.launchAnimation && !playing
    LaunchedEffect(launchEnabled) {
        launchIntensity.floatValue = 0f
        if (!launchEnabled) return@LaunchedEffect
        val detector = LaunchDetector()
        var last = 0L
        while (true) {
            // Nothing to animate: sleep until the throttle is worth a look, rather than ticking every frame.
            if (detector.settled) {
                snapshotFlow { latest.torque }.first { it > LAUNCH_MIN_TORQUE }
                last = 0L
            }
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.1f)
            last = now
            val d = latest
            val kmh = (d.speed ?: 0.0) / (if (vehicle.imperial) KMH_TO_MPH else 1.0)
            val wasActive = detector.active
            detector.update(dt, kmh, d.torque, linked = d.warnings.none { it == RideWarning.NoLink })
            if (detector.active && !wasActive) playLaunchHaptic(context)
            launchIntensity.floatValue = detector.intensity
            // Streaks travel faster the harder and the faster you go.
            launchPhase.floatValue += dt * (0.3f + 1.1f * detector.intensity + 0.6f * (kmh / 60.0).toFloat().coerceIn(0f, 1f))
        }
    }

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
                    } else {
                        // A small push: the view swells and shifts back a touch, like being pressed into a seat.
                        val push = launchIntensity.floatValue
                        scaleX = 1f + 0.02f * push
                        scaleY = 1f + 0.02f * push
                        translationX = -0.008f * size.width * push
                    }
                },
        ) {
            Crossfade(targetState = rideStyle to palette, animationSpec = tween(450), label = "rideStyle") { (style, c) ->
                when (style) {
                    RideStyle.CLASSIC -> ClassicRide(data, c, cycleMode, onHome)
                    RideStyle.MINIMAL -> MinimalRide(data, c, cycleMode, onHome)
                    RideStyle.TILES -> TilesRide(data, c, cycleMode, onHome)
                    RideStyle.CUSTOM -> CustomRide(vm, data, c, cycleMode, onHome)
                }
            }
        }
        if (launching && launchEnabled) {
            LaunchEffect(
                intensity = { launchIntensity.floatValue },
                phase = { launchPhase.floatValue },
                glow = data.modeColor ?: palette.cyan,
                streak = palette.text,
                modifier = Modifier.fillMaxSize(),
            )
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
