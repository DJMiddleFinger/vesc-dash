package com.vescdash.ui.connect

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vescdash.ble.VescBleTransport
import com.vescdash.ui.MainViewModel
import com.vescdash.ui.common.AppButton
import com.vescdash.ui.common.SectionLabel
import com.vescdash.ui.common.StarkRing
import com.vescdash.ui.common.caps
import com.vescdash.ui.theme.Palette

private fun blePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun hasAll(context: Context, perms: Array<String>) =
    perms.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val permissions = remember { blePermissions() }
    var granted by remember { mutableStateOf(hasAll(context, permissions)) }
    var btOn by remember { mutableStateOf(vm.bluetoothOn()) }

    val enableBt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        btOn = vm.bluetoothOn()
        if (btOn) vm.startScan()
    }
    val requestPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.all { it }
        btOn = vm.bluetoothOn()
        if (granted && btOn) vm.startScan()
    }

    val results by vm.scanResults.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val scanError by vm.scanError.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val lastDevice by vm.lastDevice.collectAsStateWithLifecycle()
    val accent = MaterialTheme.colorScheme.primary

    fun scan() {
        when {
            !granted -> requestPerms.launch(permissions)
            !vm.bluetoothOn() -> enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            else -> vm.startScan()
        }
    }

    LaunchedEffect(Unit) {
        if (connection !is VescBleTransport.State.Connected) scan()
    }
    DisposableEffect(Unit) { onDispose { vm.stopScan() } }

    val stark = Palette.stark

    val header: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Connect to VESC".caps(),
                color = Palette.Fg,
                fontSize = if (stark) 15.sp else 22.sp,
                fontFamily = Palette.display,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (scanning) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = accent)
            } else {
                TextButton(onClick = { scan() }) { Text("Scan") }
            }
        }
    }

    val status: @Composable () -> Unit = {
        when (val c = connection) {
            is VescBleTransport.State.Connected -> {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(Palette.innerShape)
                        .background(Palette.Surface2)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name ?: "VESC", color = Palette.Fg, fontWeight = FontWeight.Bold)
                        Text(c.address, color = Palette.TextDim, fontSize = 12.sp)
                    }
                    AppButton("Disconnect", onClick = { vm.disconnect() })
                }
            }
            is VescBleTransport.State.Connecting -> Text("Connecting to ${c.address}…", color = Palette.Warn)
            is VescBleTransport.State.Failed -> Text(c.reason, color = Palette.Danger, fontSize = 13.sp)
            VescBleTransport.State.Idle -> Unit
        }

        if (!granted) {
            Text("Bluetooth permission is needed to find your VESC.", color = Palette.TextDim)
            AppButton("Grant permission", onClick = { requestPerms.launch(permissions) }, primary = true)
        } else if (!btOn) {
            Text("Bluetooth is off.", color = Palette.TextDim)
            AppButton("Turn on Bluetooth", onClick = { enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }, primary = true)
        }
        scanError?.let { Text(it, color = Palette.Warn, fontSize = 13.sp) }

        val last = lastDevice
        if (last != null && connection !is VescBleTransport.State.Connected && results.none { it.address == last }) {
            AppButton("Reconnect to last device ($last)", onClick = { vm.connect(last); onDismiss() }, modifier = Modifier.fillMaxWidth())
        }
    }

    val devices: @Composable () -> Unit = {
        SectionLabel("NEARBY DEVICES")
        LazyColumn(Modifier.heightIn(max = if (stark) 240.dp else 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(results, key = { it.address }) { d ->
                DeviceRow(d, isLast = d.address == lastDevice, accent = accent) {
                    vm.connect(d.address)
                    onDismiss()
                }
            }
            if (results.isEmpty() && !scanning && granted && btOn) {
                item {
                    Text(
                        "No devices found. Make sure the VESC is powered and not connected to VESC Tool or another phone." +
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) " Location services must be on to scan on this Android version." else "",
                        color = Palette.TextDim,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = stark),
        containerColor = Palette.Surface,
        // Stark is always landscape, so let the sheet use the width for its two columns.
        sheetMaxWidth = if (stark) 900.dp else BottomSheetDefaults.SheetMaxWidth,
    ) {
        if (stark) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StarkRing(connected = connection is VescBleTransport.State.Connected, busy = scanning || connection is VescBleTransport.State.Connecting)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        header()
                        status()
                    }
                }
                Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(12.dp)) { devices() }
            }
        } else {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                header()
                status()
                devices()
            }
        }
    }
}

@Composable
private fun DeviceRow(d: VescBleTransport.Device, isLast: Boolean, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Palette.innerShape)
            .background(Palette.Surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.name ?: "Unnamed device", color = if (d.name != null) Palette.Fg else Palette.TextDim, fontWeight = FontWeight.SemiBold)
                if (d.likelyVesc) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "VESC",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(accent).padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                if (isLast) {
                    Spacer(Modifier.width(6.dp))
                    Text("LAST USED", color = Palette.TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(d.address, color = Palette.TextDim, fontSize = 12.sp)
        }
        Text("${d.rssi} dBm", color = Palette.TextDim, fontSize = 12.sp)
    }
}
