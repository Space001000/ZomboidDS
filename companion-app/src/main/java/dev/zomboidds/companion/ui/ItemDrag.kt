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
import dev.zomboidds.companion.domain.ItemStack

/**
 * Dragging an item onto a container tab, like dragging between the game's inventory windows: the
 * state the item tiles, the tabs and the floating item share. Positions are in root coordinates.
 */
internal class ItemDrag(private val onDrop: (stack: ItemStack, toContainer: String) -> Unit) {
    var stack by mutableStateOf<ItemStack?>(null)
        private set
    var from by mutableStateOf<String?>(null)
        private set
    var position by mutableStateOf(Offset.Zero)
        private set

    /** Where each visible container tab is (clipped to what's on screen), by container id. */
    val tabs = mutableStateMapOf<String, Rect>()

    val active: Boolean get() = stack != null

    fun target(): String? = tabs.entries.firstOrNull { it.value.contains(position) }?.key

    internal fun start(stack: ItemStack, from: String, at: Offset) {
        this.stack = stack
        this.from = from
        position = at
    }

    internal fun move(to: Offset) {
        position = to
    }

    internal fun end(drop: Boolean) {
        val dragged = stack
        val target = target()
        if (drop && dragged != null && target != null && target != from) onDrop(dragged, target)
        stack = null
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
 * Makes an item tile draggable: hold it a moment, then drag. A tap stays a tap and a quick swipe
 * still scrolls; once lifted, the gesture is ours (no click on release, no scrolling).
 */
internal fun Modifier.draggableItem(stack: ItemStack, from: String): Modifier = composed {
    val drag = LocalItemDrag.current ?: return@composed this
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    this
        .onGloballyPositioned { coordinates = it }
        .pointerInput(stack.first.id, from) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // Tap, scroll or hold? Watch without consuming until the hold time is up.
                val decided = withTimeoutOrNull(HOLD_MS) {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                            ?: return@withTimeoutOrNull false
                        if (!change.pressed) return@withTimeoutOrNull false
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            return@withTimeoutOrNull false
                        }
                    }
                    @Suppress("UNREACHABLE_CODE") false
                }
                if (decided != null) return@awaitEachGesture // a tap or a scroll: not ours
                val origin = coordinates ?: return@awaitEachGesture
                drag.start(stack, from, origin.localToRoot(down.position))
                var dropped = false
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (!change.pressed) {
                        dropped = true
                        break
                    }
                    coordinates?.let { drag.move(it.localToRoot(change.position)) }
                }
                drag.end(drop = dropped)
            }
        }
}

/** Registers a container tab as a drop target while it's on screen. */
@Composable
internal fun Modifier.dropTarget(containerId: String): Modifier {
    val drag = LocalItemDrag.current ?: return this
    DisposableEffect(containerId) { onDispose { drag.tabs.remove(containerId) } }
    return this.onGloballyPositioned { drag.tabs[containerId] = it.boundsInRoot() }
}
