package dev.zomboidds.companion.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key as keyed
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * Rows of [rowHeight] with [gap] between them (lay them out in a column with that spacing), each
 * draggable by its handle, up or down. The order changes while dragging: once the row passes
 * half of its neighbour, [onMove] swaps them and the rest make room. [row] gets the handle's
 * modifier and whether it's the row being dragged.
 *
 * [afterLongPress]: the drag starts after a long press on the handle, for a list that also
 * scrolls. Without it the drag starts at once, for a list that only reorders.
 */
@Composable
internal fun <T> DragRows(
    rows: List<T>,
    key: (T) -> Any,
    rowHeight: Dp,
    gap: Dp,
    onMove: (from: Int, to: Int) -> Unit,
    afterLongPress: Boolean = true,
    row: @Composable (item: T, handle: Modifier, lifted: Boolean) -> Unit,
) {
    val step = with(LocalDensity.current) { (rowHeight + gap).toPx() }
    var dragging by remember { mutableStateOf<Any?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    // The gesture outlives recompositions (rows keep their identity by key), so it reads the latest.
    val currentRows by rememberUpdatedState(rows)
    val currentOnMove by rememberUpdatedState(onMove)
    rows.forEach { item ->
        val id = key(item)
        keyed(id) {
            val isDragged = dragging == id
            val handle = Modifier.pointerInput(id, afterLongPress) {
                // Where the row is now: moves happen before the list recomposes.
                var at = 0
                val onStart = { _: Offset ->
                    at = currentRows.indexOfFirst { key(it) == id }
                    dragging = id; offset = 0f
                }
                val onEnd = { dragging = null; offset = 0f }
                val onDrag = { change: PointerInputChange, amount: Offset ->
                    change.consume()
                    offset += amount.y
                    val to = (at + (offset / step).roundToInt()).coerceIn(0, currentRows.lastIndex)
                    if (at >= 0 && to != at) {
                        currentOnMove(at, to)
                        offset -= (to - at) * step
                        at = to
                    }
                }
                if (afterLongPress) {
                    detectDragGesturesAfterLongPress(onDragStart = onStart, onDragEnd = onEnd, onDragCancel = onEnd, onDrag = onDrag)
                } else {
                    detectDragGestures(onDragStart = onStart, onDragEnd = onEnd, onDragCancel = onEnd, onDrag = onDrag)
                }
            }
            Box(
                Modifier
                    .zIndex(if (isDragged) 1f else 0f)
                    .offset { IntOffset(0, if (isDragged) offset.roundToInt() else 0) },
            ) {
                row(item, handle, isDragged)
            }
        }
    }
}
