package com.vescdash.ui.ride

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vescdash.data.Corner
import com.vescdash.data.DashWidget
import com.vescdash.data.Defaults
import com.vescdash.data.Guides
import com.vescdash.data.LayoutGeometry
import com.vescdash.data.RideItem
import com.vescdash.data.RideLayout
import com.vescdash.data.RideRect
import com.vescdash.data.Snapped
import com.vescdash.data.newId
import com.vescdash.ui.MainViewModel
import com.vescdash.ui.orientationFor
import com.vescdash.ui.dash.WidgetEditorSheet
import com.vescdash.ui.theme.Palette
import kotlin.math.min

/** What the editor has selected: a placed widget, or one of the two fixed elements. */
private sealed interface Sel {
    data class Item(val id: String) : Sel
    data object Pill : Sel
    data object Warnings : Sel
}

/** Everything on screen from bottom to top: the widgets, then the mode pill and the warnings. */
private fun RideLayout.elements(): List<Pair<Sel, RideRect>> =
    items.map { Sel.Item(it.widget.id) to it.rect } + (Sel.Pill to modePill) + (Sel.Warnings to warnings)

private fun RideLayout.rect(s: Sel): RideRect = elements().first { it.first == s }.second

private fun RideLayout.widget(s: Sel): DashWidget? =
    (s as? Sel.Item)?.let { i -> items.firstOrNull { it.widget.id == i.id }?.widget }

/**
 * Whether the toolbar should sit on the bottom edge rather than the top, so it covers as little as
 * possible: away from the selected element, or with nothing selected, on the edge with fewer elements
 * under it (the top on a tie). [band] is how much of the screen height the toolbar covers, as a fraction.
 */
private fun RideLayout.toolbarAtBottom(sel: Sel?, band: Float): Boolean {
    fun RideRect.underTop() = y < band
    fun RideRect.underBottom() = bottom > 1f - band
    if (sel != null) return rect(sel).let { it.underTop() && !it.underBottom() }
    val rects = elements().map { it.second }
    return rects.count { it.underBottom() } < rects.count { it.underTop() }
}

private fun RideLayout.withRect(s: Sel, r: RideRect): RideLayout = when (s) {
    is Sel.Item -> copy(items = items.map { if (it.widget.id == s.id) it.copy(rect = r) else it })
    Sel.Pill -> copy(modePill = r)
    Sel.Warnings -> copy(warnings = r)
}

private fun Rect.corner(c: Corner) = Offset(if (c.right) right else left, if (c.bottom) bottom else top)

/** The draft layout being edited, what's selected, and the undo history. */
private class EditorState(private val initial: RideLayout) {
    var draft by mutableStateOf(initial)
        private set
    var selected by mutableStateOf<Sel?>(null)
    var snap by mutableStateOf(true)

    /** Guide lines the current gesture has snapped to. */
    var guides by mutableStateOf(Guides.None)
    var gesturing by mutableStateOf(false)
    private val undoStack = mutableStateListOf<RideLayout>()

    val dirty: Boolean get() = draft != initial
    val canUndo: Boolean get() = undoStack.isNotEmpty()

    /** Live change while a gesture runs; [endGesture] files the single undo step. */
    fun setRect(sel: Sel, r: RideRect) {
        draft = draft.withRect(sel, r)
    }

    fun endGesture(before: RideLayout) {
        if (draft != before) undoStack.add(before)
    }

    private fun commit(new: RideLayout) {
        if (new == draft) return
        undoStack.add(draft)
        draft = new
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        draft = undoStack.removeAt(undoStack.lastIndex)
        if (selected?.let { s -> draft.elements().none { it.first == s } } == true) selected = null
    }

    /** Adds a new widget in the middle of the screen, or updates the one with the same id. */
    fun upsert(w: DashWidget, geo: LayoutGeometry) {
        val existing = draft.items.firstOrNull { it.widget.id == w.id }
        if (existing == null) {
            commit(draft.copy(items = draft.items + RideItem(w, geo.defaultRect(w.type))))
            selected = Sel.Item(w.id)
        } else {
            // A different type may need more room.
            val rect = geo.clamp(existing.rect, geo.minSizeDp(w.type))
            commit(draft.copy(items = draft.items.map { if (it.widget.id == w.id) RideItem(w, rect) else it }))
        }
    }

    fun duplicate(id: String, geo: LayoutGeometry) {
        val item = draft.items.first { it.widget.id == id }
        val copy = RideItem(item.widget.copy(id = newId()), geo.nudged(item.rect, 16f))
        commit(draft.copy(items = draft.items + copy))
        selected = Sel.Item(copy.widget.id)
    }

    fun reorder(id: String, toFront: Boolean) {
        val item = draft.items.first { it.widget.id == id }
        val rest = draft.items - item
        commit(draft.copy(items = if (toFront) rest + item else listOf(item) + rest))
    }

    fun delete(id: String) {
        commit(draft.copy(items = draft.items.filterNot { it.widget.id == id }))
        selected = null
    }

    fun reset() {
        commit(Defaults.rideLayout)
        selected = null
    }
}

private data class SheetTarget(val widget: DashWidget?)

private enum class Confirm { DISCARD, RESET }

/** Corner handles are drawn small but grab from this far across. */
private const val HANDLE_TOUCH_DP = 44

/** The toolbar's height plus its margin: how far down from an edge it covers. */
private const val TOOLBAR_BAND_DP = 72f

/**
 * Full-screen editor for the Custom ride layout: drag widgets, pinch anywhere to resize the
 * selected one, drag a corner handle to resize freely. Works on a draft; Save writes it.
 */
@Composable
internal fun RideLayoutEditor(vm: MainViewModel) {
    val saved by vm.rideLayout.collectAsStateWithLifecycle()
    val vehicle by vm.vehicle.collectAsStateWithLifecycle()
    val d = collectRideData(vm)
    val c = ridePalette(vehicle.rideTheme)
    val accent = MaterialTheme.colorScheme.primary
    val state = remember { EditorState(saved) }
    var sheet by remember { mutableStateOf<SheetTarget?>(null) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val toolbarAlpha by animateFloatAsState(if (state.gesturing) 0.25f else 1f, label = "toolbar")

    // The editor is landscape-only, whatever way the phone is held.
    val view = LocalView.current
    DisposableEffect(view) {
        val activity = view.context.findActivity()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity?.requestedOrientation = orientationFor(vehicle.appearance) }
    }
    ImmersiveMode()

    val cancel = { if (state.dirty) confirm = Confirm.DISCARD else vm.closeLayoutEditor() }
    BackHandler(onBack = cancel)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val geo = remember(maxWidth, maxHeight) { LayoutGeometry(maxWidth.value, maxHeight.value) }

        RideLayoutView(vm, state.draft, d, c, Modifier.fillMaxSize(), samples = true)

        Canvas(Modifier.fillMaxSize().pointerInput(geo) { editGestures(state, geo) }) {
            val hair = 1.dp.toPx()
            val dashed = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            for ((sel, r) in state.draft.elements()) {
                val rect = r.toRect(size.width, size.height)
                val radius = CornerRadius(
                    when (sel) {
                        is Sel.Item -> min(rect.width, rect.height) * CARD_CORNER
                        Sel.Pill -> rect.height / 2f
                        Sel.Warnings -> 8.dp.toPx()
                    },
                )
                if (sel == state.selected) {
                    drawRoundRect(accent, rect.topLeft, rect.size, radius, style = Stroke(2.dp.toPx()))
                    for (corner in Corner.entries) {
                        drawCircle(Color.White, 6.dp.toPx(), rect.corner(corner))
                        drawCircle(accent, 6.dp.toPx(), rect.corner(corner), style = Stroke(2.dp.toPx()))
                    }
                } else {
                    drawRoundRect(c.textSoft.copy(alpha = 0.35f), rect.topLeft, rect.size, radius, style = Stroke(hair, pathEffect = dashed))
                }
            }
            for (x in state.guides.xs) drawLine(c.cyan, Offset(x * size.width, 0f), Offset(x * size.width, size.height), hair)
            for (y in state.guides.ys) drawLine(c.cyan, Offset(0f, y * size.height), Offset(size.width, y * size.height), hair)
        }

        // Slides to the other edge when it would cover the selection, and fades while a finger is down.
        val atBottom = state.draft.toolbarAtBottom(state.selected, TOOLBAR_BAND_DP / maxHeight.value)
        val toolbarEdge by animateFloatAsState(if (atBottom) 1f else -1f, label = "toolbarEdge")
        Surface(
            modifier = Modifier
                .align(BiasAlignment(0f, toolbarEdge))
                .padding(vertical = 8.dp)
                .graphicsLayer { alpha = toolbarAlpha },
            shape = RoundedCornerShape(28.dp),
            color = c.panel.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, c.panelLine),
        ) {
            Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = cancel) { Text("Cancel", color = c.text) }
                ToolButton(Icons.AutoMirrored.Filled.Undo, "Undo", c.text, enabled = state.canUndo, onClick = state::undo)
                ToolButton(Icons.Filled.Add, "Add widget", c.text) { sheet = SheetTarget(null) }
                ToolButton(Icons.Filled.RestartAlt, "Reset to default", c.text) { confirm = Confirm.RESET }
                ToolButton(Icons.Filled.GridOn, if (state.snap) "Snapping on" else "Snapping off", if (state.snap) accent else c.label) {
                    state.snap = !state.snap
                }
                val sel = state.selected
                val widget = sel?.let { state.draft.widget(it) }
                if (sel is Sel.Item && widget != null) {
                    ToolButton(Icons.Filled.Edit, "Edit widget", c.text) { sheet = SheetTarget(widget) }
                    ToolButton(Icons.Filled.ContentCopy, "Duplicate", c.text) { state.duplicate(sel.id, geo) }
                    ToolButton(Icons.Filled.FlipToFront, "Bring to front", c.text) { state.reorder(sel.id, toFront = true) }
                    ToolButton(Icons.Filled.FlipToBack, "Send to back", c.text) { state.reorder(sel.id, toFront = false) }
                    ToolButton(Icons.Filled.Delete, "Delete", c.red) { state.delete(sel.id) }
                }
                Button(
                    onClick = {
                        vm.saveRideLayout(state.draft)
                        vm.closeLayoutEditor()
                    },
                ) { Text("Save") }
            }
        }

        sheet?.let { t ->
            WidgetEditorSheet(
                initial = t.widget,
                vehicle = vehicle,
                onDismiss = { sheet = null },
                onSave = {
                    state.upsert(it, geo)
                    sheet = null
                },
                showSize = false,
            )
        }
    }

    if (confirm == Confirm.DISCARD) {
        ConfirmDialog(
            "Discard changes?", "Your edits to the custom layout will be lost.", "Discard", "Keep editing",
            onConfirm = {
                confirm = null
                vm.closeLayoutEditor()
            },
            onDismiss = { confirm = null },
        )
    }
    if (confirm == Confirm.RESET) {
        ConfirmDialog(
            "Reset to default?", "Replaces the layout with the default one. You can still undo it.", "Reset", "Cancel",
            onConfirm = {
                confirm = null
                state.reset()
            },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun ToolButton(icon: ImageVector, description: String, tint: Color, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = description, tint = tint.copy(alpha = if (enabled) 1f else 0.35f))
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, action: String, dismiss: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Dialog,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss) } },
    )
}

/**
 * One finger on an element selects it, and dragging moves it (or, from a corner handle of the
 * selection, resizes it). A second finger anywhere turns the gesture into a pinch that resizes
 * the selection about its centre. A tap on empty space deselects. Snapping applies to moves and
 * corner drags; each gesture is one undo step.
 */
private suspend fun PointerInputScope.editGestures(s: EditorState, geo: LayoutGeometry) {
    val slop = viewConfiguration.touchSlop
    val reach = HANDLE_TOUCH_DP.dp.toPx() / 2f
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        val before = s.draft

        // The first finger lands on a corner handle of the selection, on an element, or on nothing.
        val handle = s.selected?.let { sel ->
            val r = before.rect(sel).toRect(w, h)
            Corner.entries.minBy { (r.corner(it) - down.position).getDistance() }
                .takeIf { (r.corner(it) - down.position).getDistance() <= reach }
        }
        val hit = if (handle == null) {
            before.elements().lastOrNull { it.second.toRect(w, h).contains(down.position) }?.first
        } else {
            null
        }
        if (hit != null) s.selected = hit
        val canDrag = handle != null || hit != null

        // Each time a finger is added or lifted, later movement is measured from that moment.
        var fingers = 0
        var baseRect = RideRect(0f, 0f, 0f, 0f)
        var basePos = down.position
        var baseSpread = 0f
        var lines = Guides.None
        var pinched = false
        var dragging = false
        var travelled = false
        fun rebase(touches: List<PointerInputChange>) {
            val sel = s.selected ?: return
            fingers = touches.size
            baseRect = geo.clamp(s.draft.rect(sel), geo.minSizeDp(s.draft.widget(sel)?.type))
            basePos = touches[0].position
            baseSpread = if (fingers >= 2) (touches[0].position - touches[1].position).getDistance() else 0f
            lines = geo.guideLines(s.draft.elements().filter { it.first != sel }.map { it.second })
            if (fingers >= 2) pinched = true
        }
        rebase(listOf(down))
        s.gesturing = true

        do {
            val event = awaitPointerEvent()
            val touches = event.changes.filter { it.pressed }
            val sel = s.selected
            if (touches.isNotEmpty()) {
                if ((touches[0].position - down.position).getDistance() > slop) travelled = true
                if (sel != null) {
                    if (touches.size != fingers) rebase(touches)
                    val minDp = geo.minSizeDp(s.draft.widget(sel)?.type)
                    if (touches.size >= 2) {
                        val spread = (touches[0].position - touches[1].position).getDistance()
                        if (baseSpread > 0f) s.setRect(sel, geo.scaled(baseRect, spread / baseSpread, minDp))
                        s.guides = Guides.None
                    } else if (canDrag && !pinched) {
                        val delta = touches[0].position - basePos
                        if (dragging || delta.getDistance() > slop) {
                            dragging = true
                            val raw = if (handle != null) {
                                geo.dragCorner(baseRect, handle, delta.x / w, delta.y / h, minDp)
                            } else {
                                geo.moved(baseRect, delta.x / w, delta.y / h)
                            }
                            val snapped = when {
                                !s.snap -> Snapped(raw, Guides.None)
                                handle != null -> geo.snapCorner(raw, handle, lines, minDp)
                                else -> geo.snapMove(raw, lines)
                            }
                            s.setRect(sel, snapped.rect)
                            s.guides = snapped.guides
                        }
                    }
                }
            }
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })

        s.gesturing = false
        s.guides = Guides.None
        s.endGesture(before)
        if (!canDrag && !travelled && !pinched) s.selected = null
    }
}
