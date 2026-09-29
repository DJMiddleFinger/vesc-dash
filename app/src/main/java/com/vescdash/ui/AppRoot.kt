package com.vescdash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vescdash.ble.VescBleTransport
import com.vescdash.ui.connect.ConnectSheet
import com.vescdash.ui.dash.DashboardScreen
import com.vescdash.ui.modes.ModesScreen
import com.vescdash.ui.setup.SetupScreen
import com.vescdash.ui.theme.Palette
import com.vescdash.ui.theme.VescDashTheme

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

    val activeMode = modes.firstOrNull { it.id == activeId } ?: modes.firstOrNull()
    val accent = activeMode?.let { Color(it.color) } ?: Palette.DefaultAccent

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showConnect by remember { mutableStateOf(false) }

    val connected = connection is VescBleTransport.State.Connected
    val view = LocalView.current
    DisposableEffect(vehicle.keepScreenOn, connected) {
        view.keepScreenOn = vehicle.keepScreenOn && connected
        onDispose { view.keepScreenOn = false }
    }

    val (dotColor, statusText) = when {
        connected && stale -> Palette.Warn to "No data"
        connected -> Palette.Good to (firmware?.let { "FW ${it.major}.${it.minor.toString().padStart(2, '0')}" } ?: "Connected")
        connection is VescBleTransport.State.Connecting -> Palette.Warn to "Connecting…"
        reconnecting -> Palette.Warn to "Reconnecting…"
        connection is VescBleTransport.State.Failed -> Palette.Danger to "Disconnected"
        else -> Palette.TextDim to "Connect"
    }

    VescDashTheme(accent) {
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
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> DashboardScreen(vm, onConnectClick = { showConnect = true })
                    1 -> ModesScreen(vm)
                    else -> SetupScreen(vm)
                }
            }
        }
        if (showConnect) ConnectSheet(vm, onDismiss = { showConnect = false })
    }
}
