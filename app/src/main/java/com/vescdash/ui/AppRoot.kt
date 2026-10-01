package com.vescdash.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vescdash.ble.VescBleTransport
import com.vescdash.data.AppAppearance
import com.vescdash.data.VehicleMath
import com.vescdash.ui.connect.ConnectSheet
import com.vescdash.ui.dash.DashboardScreen
import com.vescdash.ui.modes.ModesScreen
import com.vescdash.ui.ride.RideLayoutEditor
import com.vescdash.ui.ride.RideScreen
import com.vescdash.ui.ride.findActivity
import com.vescdash.ui.setup.SetupScreen
import com.vescdash.ui.theme.Palette
import com.vescdash.ui.theme.VescDashTheme
import kotlin.math.abs

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Dash", Icons.Filled.Speed),
    Tab("Modes", Icons.Filled.Tune),
    Tab("Setup", Icons.Filled.Settings),
)

@Composable
fun AppRoot(vm: MainViewModel) {
    val modes by vm.modes.collectAsStateWithLifecycle()
    val activeId by vm.activeModeId.collectAsStateWithLifecycle()
    val vehicle by vm.vehicle.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val stale by vm.stale.collectAsStateWithLifecycle()
    val reconnecting by vm.reconnecting.collectAsStateWithLifecycle()
    val firmware by vm.firmware.collectAsStateWithLifecycle()
    val demoActive by vm.demoActive.collectAsStateWithLifecycle()
    val layoutEditorOpen by vm.layoutEditorOpen.collectAsStateWithLifecycle()
    val newController by vm.newController.collectAsStateWithLifecycle()
    val telemetryState = vm.telemetry.collectAsStateWithLifecycle()

    val activeMode = modes.firstOrNull { it.id == activeId } ?: modes.firstOrNull()
    val accent = activeMode?.let { Color(it.color) } ?: Palette.DefaultAccent

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showConnect by remember { mutableStateOf(false) }
    val appearance = vehicle.appearance
    val stark = appearance == AppAppearance.STARK

    val connected = connection is VescBleTransport.State.Connected

    // Landscape = full-screen ride view. The home button drops back to the normal app;
    // riding off (> 5 km/h) or rotating to portrait brings the ride view back.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var homeInLandscape by rememberSaveable { mutableStateOf(false) }
    // Stark can't use the phone's orientation to tell a ride from the app (it is always landscape):
    // the ride view opens on riding off or from the RIDE button, and the home button closes it.
    var rideOpen by rememberSaveable { mutableStateOf(false) }
    val movingFast by remember {
        derivedStateOf {
            telemetryState.value?.let { abs(VehicleMath.speedKmhForErpm(it.erpm, vm.vehicle.value)) > 5.0 } == true
        }
    }
    LaunchedEffect(movingFast, landscape) {
        if (movingFast || !landscape) homeInLandscape = false
    }
    // Don't carry an open ride view over from one appearance to the other.
    LaunchedEffect(appearance) { rideOpen = false }
    LaunchedEffect(movingFast) {
        if (movingFast) rideOpen = true
    }
    val view = LocalView.current
    DisposableEffect(appearance) {
        val activity = view.context.findActivity()
        activity?.requestedOrientation = orientationFor(appearance)
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    DisposableEffect(vehicle.keepScreenOn, connected) {
        view.keepScreenOn = vehicle.keepScreenOn && connected
        onDispose { view.keepScreenOn = false }
    }

    val (dotColor, statusText) = when {
        connected && stale -> Palette.Warn to "No data"
        connected -> Palette.Good to (firmware?.let { "FW ${it.major}.${it.minor.toString().padStart(2, '0')}" } ?: "Connected")
        connection is VescBleTransport.State.Connecting -> Palette.Warn to "Connecting…"
        demoActive -> Palette.Warn to "Demo"
        reconnecting -> Palette.Warn to "Reconnecting…"
        connection is VescBleTransport.State.Failed -> Palette.Danger to "Disconnected"
        else -> Palette.TextDim to "Connect"
    }

    val showRide = showRideView(appearance, landscape, homeInLandscape, rideOpen)

    // The editor turns the phone landscape. Opened from the normal app, stay on it when the editor
    // closes rather than flashing the ride view while the phone turns back to portrait.
    LaunchedEffect(layoutEditorOpen) {
        if (layoutEditorOpen && !showRide) homeInLandscape = true
    }

    VescDashTheme(accent, appearance) {
        // Entering the ride view zooms in from slightly larger; leaving shrinks it back out.
        AnimatedContent(
            targetState = showRide,
            modifier = Modifier.fillMaxSize().background(Palette.Bg),
            transitionSpec = {
                if (targetState) {
                    (fadeIn(tween(450, delayMillis = 60)) + scaleIn(tween(600, easing = FastOutSlowInEasing), initialScale = 1.12f)) togetherWith
                        (fadeOut(tween(250)) + scaleOut(tween(300), targetScale = 0.94f))
                } else {
                    (fadeIn(tween(350, delayMillis = 80)) + scaleIn(tween(400, easing = FastOutSlowInEasing), initialScale = 0.94f)) togetherWith
                        (fadeOut(tween(250)) + scaleOut(tween(300), targetScale = 1.1f))
                }
            },
            label = "rideView",
        ) { ride ->
            if (ride) {
                // The editor covers the ride view, so leave it out meanwhile: it re-enters afterwards
                // and hides the system bars again, which the editor's own exit would have shown.
                if (!layoutEditorOpen) RideScreen(vm, onHome = { homeInLandscape = true; rideOpen = false })
            } else if (stark) {
                Row(Modifier.fillMaxSize().background(Palette.Bg)) {
                    StarkRail(
                        selected = tab,
                        onSelect = { tab = it },
                        statusColor = dotColor,
                        statusText = statusText,
                        onRide = { homeInLandscape = false; rideOpen = true },
                        onConnect = { showConnect = true },
                    )
                    TabContent(
                        tab, vm,
                        Modifier.weight(1f).fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.End)),
                    ) { showConnect = true }
                }
            } else {
                Scaffold(
                    containerColor = Palette.Bg,
                    topBar = {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("VESC", color = accent, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 2.sp)
                            Text(" DASH", color = Palette.Fg, fontWeight = FontWeight.Light, fontSize = 18.sp, letterSpacing = 2.sp)
                            Spacer(Modifier.weight(1f))
                            if (landscape) {
                                Text(
                                    "RIDE VIEW",
                                    modifier = Modifier
                                        .padding(end = 10.dp)
                                        .clip(CircleShape)
                                        .background(accent)
                                        .clickable { homeInLandscape = false; rideOpen = true }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                    color = Color.Black,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                            Row(
                                Modifier
                                    .clip(CircleShape)
                                    .background(Palette.Surface2)
                                    .clickable { showConnect = true }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                                Spacer(Modifier.width(8.dp))
                                Text(statusText, color = Palette.Fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = Palette.TextDim, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    bottomBar = {
                        NavigationBar(containerColor = Palette.Surface) {
                            tabs.forEachIndexed { i, t ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Icon(t.icon, contentDescription = t.label) },
                                    label = { Text(t.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color.Black,
                                        indicatorColor = accent,
                                        unselectedIconColor = Palette.TextDim,
                                        unselectedTextColor = Palette.TextDim,
                                        selectedTextColor = Palette.Fg,
                                    ),
                                )
                            }
                        }
                    },
                ) { padding ->
                    TabContent(tab, vm, Modifier.fillMaxSize().padding(padding)) { showConnect = true }
                }
            }
        }
        if (showConnect && !showRide) ConnectSheet(vm, onDismiss = { showConnect = false })
        if (layoutEditorOpen) RideLayoutEditor(vm)
        newController?.let { c ->
            // A new vehicle must not get the last vehicle's base limits sent to it unseen, so the choice is required.
            AlertDialog(
                onDismissRequest = {},
                containerColor = Palette.Dialog,
                title = { Text("New controller") },
                text = {
                    Text(
                        "${c.name} has no saved settings. Start from your current vehicle setup and modes, or from defaults? " +
                            "Check Base limits in Setup before riding; the mode isn't applied until you choose.",
                    )
                },
                confirmButton = { TextButton(onClick = { vm.adoptController(fresh = false) }) { Text("Copy current") } },
                dismissButton = { TextButton(onClick = { vm.adoptController(fresh = true) }) { Text("Start fresh") } },
            )
        }
    }
}

@Composable
private fun TabContent(tab: Int, vm: MainViewModel, modifier: Modifier, onConnect: () -> Unit) {
    AnimatedContent(
        targetState = tab,
        modifier = modifier,
        transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { w -> dir * w / 5 } + fadeIn(tween(260))) togetherWith
                (slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { w -> -dir * w / 5 } + fadeOut(tween(180)))
        },
        label = "tabs",
    ) { t ->
        when (t) {
            0 -> DashboardScreen(vm, onConnectClick = onConnect)
            1 -> ModesScreen(vm)
            else -> SetupScreen(vm)
        }
    }
}
