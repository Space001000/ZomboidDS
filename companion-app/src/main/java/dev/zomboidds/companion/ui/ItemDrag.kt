package dev.zomboidds.companion.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.dp
import dev.zomboidds.companion.domain.ItemStack

/**
 * Dragging an item onto a container tab, like dragging between the game's inventory windows: the
 * state the item tiles, the tabs and the floating item share. Positions are in root coordinates.
 */
internal class ItemDrag(
    /** What a drag from a stack carries: that stack, or everything picked with it (it first). */
    private val carried: (stack: ItemStack, from: String) -> List<ItemStack>,
    private val onDrop: (stacks: List<ItemStack>, toContainer: String) -> Unit,
) {
    var stacks by mutableStateOf<List<ItemStack>>(emptyList())
        private set

    /** The stack under the finger. */
    val stack: ItemStack? get() = stacks.firstOrNull()
    var from by mutableStateOf<String?>(null)
        private set
    var position by mutableStateOf(Offset.Zero)
        private set

    /** Where each visible container tab is (clipped to what's on screen), by container id. */
    val tabs = mutableStateMapOf<String, Rect>()

    /** Where each open container's items are shown, by container id: dropping there works too. */
    val panes = mutableStateMapOf<String, Rect>()

    val active: Boolean get() = stacks.isNotEmpty()

    fun target(): String? = (tabs.entries.firstOrNull { it.value.contains(position) }
        ?: panes.entries.firstOrNull { it.value.contains(position) })?.key

    internal fun start(stack: ItemStack, from: String, at: Offset) {
        stacks = carried(stack, from)
        this.from = from
        position = at
    }

    internal fun move(to: Offset) {
        position = to
    }

    internal fun end(drop: Boolean) {
        val dragged = stacks
        val target = target()
        if (drop && dragged.isNotEmpty() && target != null && target != from) onDrop(dragged, target)
        stacks = emptyList()
        from = null
    }
}

internal val LocalItemDrag = staticCompositionLocalOf<ItemDrag?> { null }

/**
 * How long a finger must rest on an item before it lifts: long enough to tell it from a tap or a
 * scroll, shorter than the usual long press so dragging feels quick.
 */
private const val HOLD_MS = 250L

/**
 * A resting finger wobbles a little: once an item is lifted, it only starts dragging past this, so
 * holding it and letting go picks it instead.
 */
private val DragSlop = 14.dp

/**
 * Makes an item tile draggable: hold it a moment, then drag; or hold it and let go without moving
 * to pick it ([onHold]). A tap stays a tap and a quick swipe still scrolls; once lifted, the
 * gesture is ours (no click on release, no scrolling).
 */
internal fun Modifier.draggableItem(stack: ItemStack, from: String, onHold: () -> Unit): Modifier = composed {
    val drag = LocalItemDrag.current ?: return@composed this
    val haptics = LocalHapticFeedback.current
    val hold by rememberUpdatedState(onHold)
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    this
        .onGloballyPositioned { coordinates = it }
        .pointerInput(stack.first.id, from) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // Tap, scroll or hold? Watch without consuming until the hold time is up: the finger
                // lifting or moving before then ends the watch early (null only when time ran out).
                val endedEarly = withTimeoutOrNull(HOLD_MS) {
                    do {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    } while (change != null && change.pressed &&
                        (change.position - down.position).getDistance() <= viewConfiguration.touchSlop)
                }
                if (endedEarly != null) return@awaitEachGesture // a tap or a scroll: not ours
                val origin = coordinates ?: return@awaitEachGesture
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                // Lifted: moving on drags it; letting go where it is picks it.
                val slop = DragSlop.toPx()
                var dragging = false
                var released = false
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (!change.pressed) {
                        released = true
                        break
                    }
                    if (!dragging && (change.position - down.position).getDistance() > slop) {
                        dragging = true
                        drag.start(stack, from, origin.localToRoot(down.position))
                    }
                    if (dragging) coordinates?.let { drag.move(it.localToRoot(change.position)) }
                }
                if (dragging) drag.end(drop = released) else if (released) hold()
            }
        }
}

/** Registers a container tab as a drop target while it's on screen. */
@Composable
internal fun Modifier.dropTarget(containerId: String): Modifier = dropArea(containerId) { tabs }

/** Registers the area showing an open container's items as a drop target for that container. */
@Composable
internal fun Modifier.paneDropTarget(containerId: String): Modifier = dropArea(containerId) { panes }

@Composable
private fun Modifier.dropArea(containerId: String, areas: ItemDrag.() -> MutableMap<String, Rect>): Modifier {
    val drag = LocalItemDrag.current ?: return this
    DisposableEffect(containerId) { onDispose { drag.areas().remove(containerId) } }
    return this.onGloballyPositioned { drag.areas()[containerId] = it.boundsInRoot() }
}
