package com.vescdash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vescdash.ui.theme.Palette
import com.vescdash.ui.theme.WideFont

private class StarkTab(val label: String, val icon: ImageVector)

private val starkTabs = listOf(
    StarkTab("DASH", Icons.Filled.Speed),
    StarkTab("MODES", Icons.Filled.Tune),
    StarkTab("SETUP", Icons.Filled.Settings),
)

/**
 * Stark's three-tab bar turned on its side for landscape: the ride-view button on top, the tabs in
 * the middle with a short red bar on the edge facing the content, and the connection status at the foot.
 */
@Composable
internal fun StarkRail(
    selected: Int,
    onSelect: (Int) -> Unit,
    statusColor: Color,
    statusText: String,
    onRide: () -> Unit,
    onConnect: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxHeight()
            .background(Palette.Chrome)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
            .width(76.dp)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.clip(CircleShape).clickable(onClick = onRide).padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFD0D0D0)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.TwoWheeler, contentDescription = "Ride view", tint = Color(0xFF1A1A1C), modifier = Modifier.size(22.dp))
            }
            Text("RIDE", color = Palette.TextDim, fontFamily = WideFont, fontSize = 8.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Spacer(Modifier.weight(1f))
        starkTabs.forEachIndexed { i, t ->
            val active = selected == i
            val tint = if (active) Palette.Fg else Palette.TextDim
            Box(Modifier.fillMaxWidth().clickable { onSelect(i) }.padding(vertical = 10.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(t.icon, contentDescription = t.label, tint = tint, modifier = Modifier.size(24.dp))
                    Text(t.label, color = tint, fontFamily = WideFont, fontSize = 8.sp, letterSpacing = 1.sp, maxLines = 1, softWrap = false, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp))
                }
                if (active) {
                    Box(Modifier.align(Alignment.CenterEnd).width(3.dp).height(30.dp).background(Palette.Red))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth().clickable(onClick = onConnect).padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                Icon(Icons.Filled.Bluetooth, contentDescription = "Connection", tint = statusColor, modifier = Modifier.size(22.dp))
                Box(Modifier.align(Alignment.TopEnd).size(6.dp).clip(CircleShape).background(statusColor))
            }
            Text(statusText, color = Palette.TextDim, fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
