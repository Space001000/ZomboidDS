package dev.zomboidds.companion.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import dev.zomboidds.companion.domain.ItemStack
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Drawing a box over a pane's items to pick them, like dragging from empty space in the game's
 * inventory window. The tiles say where they are (root coordinates, by the stack's first item); the
 * box, in the pane's own coordinates, is drawn while the finger moves.
 */
internal class BoxSelect {
    val tiles = mutableMapOf<Any, Pair<ItemStack, Rect>>()
    /** Items it can't pick (your worn clothes): no box starts on them either, so they scroll. */
    val solid = mutableMapOf<Any, Rect>()
    var from by mutableStateOf<Offset?>(null)
    var to by mutableStateOf<Offset?>(null)
}

/** A tile the box can pick, while it's on screen; [key] tells apart a stack and its first item unfolded. */
@Composable
internal fun Modifier.boxTile(select: BoxSelect, stack: ItemStack, key: Any = stack.first.id): Modifier {
    DisposableEffect(select, key) { onDispose { select.tiles.remove(key) } }
    return this.onGloballyPositioned { select.tiles[key] = stack to it.boundsInRoot() }
}

/** A tile the box can't pick but mustn't start on: a swipe there scrolls the grid. */
@Composable
internal fun Modifier.boxSolid(select: BoxSelect, key: Any): Modifier {
    DisposableEffect(select, key) { onDispose { select.solid.remove(key) } }
    return this.onGloballyPositioned { select.solid[key] = it.boundsInRoot() }
}

/**
 * The area of a pane where a box can start: on empty space, not on an item (a finger on an item
 * taps, holds or drags it). Once the finger moves, the gesture is the box's (the grid doesn't
 * scroll); everything it touches is picked, on top of what [picked] already was. A tap on empty
 * space lets go of what's picked ([onEmptyTap]).
 */
internal fun Modifier.boxSelect(
    select: BoxSelect,
    picked: Set<Long>,
    onPick: (Set<Long>) -> Unit,
    onEmptyTap: () -> Unit,
): Modifier = composed {
    val alreadyPicked by rememberUpdatedState(picked)
    val pick by rememberUpdatedState(onPick)
    val emptyTap by rememberUpdatedState(onEmptyTap)
    val color = MaterialTheme.colorScheme.primary
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    this
        .onGloballyPositioned { coordinates = it }
        .pointerInput(select) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val area = coordinates ?: return@awaitEachGesture
                val start = area.localToRoot(down.position)
                if (select.tiles.values.any { (_, bounds) -> bounds.contains(start) }) return@awaitEachGesture
                if (select.solid.values.any { it.contains(start) }) return@awaitEachGesture
                val base = alreadyPicked
                var boxing = false
                var released = false
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        released = true
                        break
                    }
                    if (!boxing && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) boxing = true
                    if (!boxing) continue
                    change.consume()
                    select.from = down.position
                    select.to = change.position
                    val end = area.localToRoot(change.position)
                    val box = Rect(min(start.x, end.x), min(start.y, end.y), max(start.x, end.x), max(start.y, end.y))
                    val hits = select.tiles.values.filter { (_, bounds) -> bounds.overlaps(box) }
                    pick(base + hits.flatMap { (stack, _) -> stack.items.map { it.id } })
                }
                select.from = null
                select.to = null
                if (released && !boxing) emptyTap()
            }
        }
        .drawWithContent {
            drawContent()
            val a = select.from ?: return@drawWithContent
            val b = select.to ?: return@drawWithContent
            val topLeft = Offset(min(a.x, b.x), min(a.y, b.y))
            val size = Size(abs(a.x - b.x), abs(a.y - b.y))
            drawRect(color.copy(alpha = 0.14f), topLeft, size)
            drawRect(color, topLeft, size, style = Stroke(1.5.dp.toPx()))
        }
}
