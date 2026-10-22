package dev.zomboidds.companion.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * Rows of [rowHeight] with [gap] between them (lay them out in a column with that spacing), each
 * draggable by its handle: a long press on the handle, then up or down. [row] gets the handle's
 * modifier and whether it's the row being dragged.
 */
@Composable
internal fun <T> DragRows(
    rows: List<T>,
    key: (T) -> Any,
    rowHeight: Dp,
    gap: Dp,
    onMove: (from: Int, to: Int) -> Unit,
    row: @Composable (item: T, handle: Modifier, lifted: Boolean) -> Unit,
) {
    val step = with(LocalDensity.current) { (rowHeight + gap).toPx() }
    var dragging by remember { mutableStateOf<Any?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    rows.forEachIndexed { index, item ->
        val id = key(item)
        val isDragged = dragging == id
        val handle = Modifier.pointerInput(id, rows.size) {
            detectDragGesturesAfterLongPress(
                onDragStart = { dragging = id; offset = 0f },
                onDragEnd = {
                    val target = (index + (offset / step).roundToInt()).coerceIn(0, rows.lastIndex)
                    if (target != index) onMove(index, target)
                    dragging = null; offset = 0f
                },
                onDragCancel = { dragging = null; offset = 0f },
                onDrag = { change, amount -> change.consume(); offset += amount.y },
            )
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
