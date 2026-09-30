package com.vescdash.ui.dash

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vescdash.ble.VescBleTransport
import com.vescdash.data.DashWidget
import com.vescdash.data.Dashboard
import com.vescdash.data.DriveMode
import com.vescdash.data.ModeApplyStatus
import com.vescdash.data.Telemetry
import com.vescdash.data.VehicleSettings
import com.vescdash.data.WidgetSize
import com.vescdash.ui.MainViewModel
import com.vescdash.ui.common.AppButton
import com.vescdash.ui.common.Banner
import com.vescdash.ui.common.appTextFieldColors
import com.vescdash.ui.common.starkUnderline
import com.vescdash.ui.theme.HeavyWideFont
import com.vescdash.ui.theme.Palette
import com.vescdash.ui.theme.WideFont
import com.vescdash.vesc.VescProtocol
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private data class EditorTarget(val dashboardId: String, val widget: DashWidget?)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(vm: MainViewModel, onConnectClick: () -> Unit) {
    val dashboards by vm.dashboards.collectAsStateWithLifecycle()
    val telemetry by vm.telemetry.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val vehicle by vm.vehicle.collectAsStateWithLifecycle()
    val modes by vm.modes.collectAsStateWithLifecycle()
    val activeId by vm.activeModeId.collectAsStateWithLifecycle()
    val modeStatus by vm.modeStatus.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val demoActive by vm.demoActive.collectAsStateWithLifecycle()

    var editing by rememberSaveable { mutableStateOf(false) }
    var editorTarget by remember { mutableStateOf<EditorTarget?>(null) }
    var renameTarget by remember { mutableStateOf<Dashboard?>(null) }
    val pager = rememberPagerState { dashboards.size }
    val scope = rememberCoroutineScope()
    val connected = connection is VescBleTransport.State.Connected
    val activeMode = modes.firstOrNull { it.id == activeId } ?: modes.firstOrNull()

    Column(Modifier.fillMaxSize()) {
        ModeStrip(modes, activeMode?.id, modeStatus, connected, onSelect = vm::selectMode)

        val fault = telemetry?.fault ?: 0
        if (fault != 0) Banner("⚠ FAULT: ${VescProtocol.faultName(fault)}", Palette.Danger)
        if (demoActive) {
            Banner("Demo mode — showing simulated data. Turn it off in Setup.", Palette.Warn)
        } else if (!connected) {
            Banner(
                "Not connected — tap here to find your VESC",
                MaterialTheme.colorScheme.primary,
                Modifier.clickable(onClick = onConnectClick),
            )
        } else if (!vehicle.configured) {
            Banner("Set motor, wheel and battery in Setup so speed and power read correctly.", Palette.Warn)
        }

        Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                dashboards.forEachIndexed { i, d ->
                    val selected = pager.currentPage == i
                    val tabColor by animateColorAsState(if (selected) Palette.Fg else Palette.TextDim, tween(250), label = "pageTab")
                    Text(
                        d.name.uppercase(),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { scope.launch { pager.animateScrollToPage(i) } }
                            .then(if (Palette.stark) Modifier.starkUnderline(selected) else Modifier)
                            .padding(horizontal = 10.dp, vertical = if (Palette.stark) 7.dp else 10.dp),
                        color = tabColor,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                        fontFamily = Palette.display,
                        letterSpacing = 1.2.sp,
                    )
                }
            }
            IconButton(onClick = { editing = !editing }) {
                Icon(
                    if (editing) Icons.Filled.Check else Icons.Filled.Edit,
                    contentDescription = if (editing) "Done editing" else "Edit dashboard",
                    tint = if (editing) MaterialTheme.colorScheme.primary else Palette.TextDim,
                )
            }
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
            key = { dashboards.getOrNull(it)?.id ?: it },
        ) { page ->
            val dash = dashboards.getOrNull(page) ?: return@HorizontalPager
            // Neighbouring pages shrink and fade slightly as you swipe between them.
            val pageModifier = Modifier.graphicsLayer {
                val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                val scale = 1f - 0.08f * offset
                scaleX = scale
                scaleY = scale
                alpha = 1f - 0.5f * offset
            }
            DashboardPage(
                modifier = pageModifier,
                dash = dash,
                telemetry = telemetry,
                history = history,
                vehicle = vehicle,
                editing = editing,
                canDeletePage = dashboards.size > 1,
                onEditWidget = { w -> editorTarget = EditorTarget(dash.id, w) },
                onMove = { id, delta -> vm.moveWidget(dash.id, id, delta) },
                onToggleSize = { id -> vm.toggleWidgetSize(dash.id, id) },
                onDelete = { id -> vm.deleteWidget(dash.id, id) },
                onRename = { renameTarget = dash },
                onAddPage = {
                    vm.addDashboard()
                    scope.launch { pager.animateScrollToPage(dashboards.size) }
                },
                onDeletePage = { vm.deleteDashboard(dash.id) },
            )
        }
    }

    editorTarget?.let { t ->
        WidgetEditorSheet(
            initial = t.widget,
            vehicle = vehicle,
            onDismiss = { editorTarget = null },
            onSave = {
                vm.upsertWidget(t.dashboardId, it)
                editorTarget = null
            },
        )
    }
    renameTarget?.let { d ->
        RenameDialog(
            initial = d.name,
            onDismiss = { renameTarget = null },
            onConfirm = {
                vm.renameDashboard(d.id, it)
                renameTarget = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardPage(
    modifier: Modifier,
    dash: Dashboard,
    telemetry: Telemetry?,
    history: List<Telemetry>,
    vehicle: VehicleSettings,
    editing: Boolean,
    canDeletePage: Boolean,
    onEditWidget: (DashWidget?) -> Unit,
    onMove: (String, Int) -> Unit,
    onToggleSize: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: () -> Unit,
    onAddPage: () -> Unit,
    onDeletePage: () -> Unit,
) {
    val width = LocalConfiguration.current.screenWidthDp
    val stark = Palette.stark
    val columns = when {
        stark -> (width / 190).coerceIn(3, 6)
        width >= 600 -> 4
        else -> 2
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(
            dash.widgets,
            key = { it.id },
            // Stark's landscape grid is wide, so a full-width widget takes two columns rather than the whole line.
            span = { GridItemSpan(if (it.size != WidgetSize.FULL) 1 else if (stark) 2 else maxLineSpan) },
        ) { w ->
            WidgetCard(
                modifier = Modifier.animateItem(),
                widget = w,
                telemetry = telemetry,
                history = history,
                historyCapacity = vehicle.historySamples,
                vehicle = vehicle,
                editing = editing,
                onEdit = { onEditWidget(w) },
                onMoveBack = { onMove(w.id, -1) },
                onMoveForward = { onMove(w.id, 1) },
                onToggleSize = { onToggleSize(w.id) },
                onDelete = { onDelete(w.id) },
            )
        }
        if (editing) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppButton("Widget", onClick = { onEditWidget(null) }, icon = Icons.Filled.Add)
                    AppButton("Rename page", onClick = onRename)
                    AppButton("New page", onClick = onAddPage)
                    if (canDeletePage) {
                        AppButton("Delete page", onClick = onDeletePage, color = Palette.Danger)
                    }
                }
            }
        } else if (dash.widgets.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "This page is empty.\nTap ✎ above to add widgets.",
                    color = Palette.TextDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                )
            }
        }
    }
}

@Composable
fun ModeStrip(
    modes: List<DriveMode>,
    activeId: String?,
    status: ModeApplyStatus,
    connected: Boolean,
    onSelect: (String) -> Unit,
) {
    if (Palette.stark) {
        StarkModeStrip(modes, activeId, status, connected, onSelect)
        return
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            modes.forEachIndexed { i, m ->
                val selected = m.id == activeId
                val c = Color(m.color)
                val shape = RoundedCornerShape(14.dp)
                val bg by animateColorAsState(if (selected) c else Palette.Surface, tween(280), label = "modeBg")
                val border by animateColorAsState(if (selected) c else Palette.Outline, tween(280), label = "modeBorder")
                val numberColor by animateColorAsState(if (selected) Color.Black else c, tween(280), label = "modeNum")
                val nameColor by animateColorAsState(if (selected) Color.Black else Palette.Fg, tween(280), label = "modeName")
                Column(
                    Modifier
                        .widthIn(min = 78.dp)
                        .clip(shape)
                        .background(bg)
                        .border(1.dp, border, shape)
                        .clickable { onSelect(m.id) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "${i + 1}",
                        color = numberColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        m.name.uppercase(),
                        color = nameColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                    )
                }
            }
        }
        val (text, color) = modeStatusText(status, connected, activeId)
        if (text.isNotEmpty()) {
            Text(text, color = color, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, start = 2.dp))
        }
    }
}

@Composable
private fun modeStatusText(status: ModeApplyStatus, connected: Boolean, activeId: String?): Pair<String, Color> = when {
    !connected -> "Mode applies when connected" to Palette.TextDim
    status is ModeApplyStatus.Applying -> "Sending mode to controller…" to Palette.Warn
    status is ModeApplyStatus.Failed -> "⚠ Controller didn't confirm — tap the mode to retry" to Palette.Danger
    status is ModeApplyStatus.Applied && status.modeId == activeId -> "✓ Active on controller" to Palette.Good
    else -> "" to Palette.TextDim
}

/** Landscape mode strip: stadium chips like the power-mode editor's, with the apply status on the same row. */
@Composable
private fun StarkModeStrip(
    modes: List<DriveMode>,
    activeId: String?,
    status: ModeApplyStatus,
    connected: Boolean,
    onSelect: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.forEachIndexed { i, m ->
                val selected = m.id == activeId
                val c = Color(m.color)
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(if (selected) Palette.Surface2 else Color(0xFF151515))
                        .border(1.dp, if (selected) c else Color(0xFF3A3A3A), CircleShape)
                        .clickable { onSelect(m.id) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}", color = c, fontFamily = HeavyWideFont, fontSize = 14.sp)
                    Text(
                        m.name.uppercase(),
                        modifier = Modifier.padding(start = 8.dp),
                        color = if (selected) Palette.Fg else Palette.TextDim,
                        fontFamily = WideFont,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                    )
                }
            }
        }
        val (text, color) = modeStatusText(status, connected, activeId)
        if (text.isNotEmpty()) Text(text, color = color, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Dialog,
        title = { Text("Page name") },
        text = { OutlinedTextField(text, { text = it.take(16) }, singleLine = true, colors = appTextFieldColors()) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim().ifBlank { initial }) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
