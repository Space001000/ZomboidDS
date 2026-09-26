package dev.zomboidds.companion.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.zomboidds.companion.ContainerLayout
import dev.zomboidds.companion.InventoryLayout
import dev.zomboidds.companion.domain.Container
import dev.zomboidds.companion.domain.ItemStack
import dev.zomboidds.companion.domain.PaneEntry
import dev.zomboidds.companion.domain.foldWorn
import dev.zomboidds.companion.domain.stacks
import kotlinx.coroutines.delay

// A container pane of the inventory screen: its tabs, the "all" action, the display toggles and the grid.
/** Tabs for [tabs], the open container's header, and its items. */
@Composable
internal fun ContainerPane(
    tabs: List<ContainerView>,
    shown: ContainerView,
    onOpen: (String) -> Unit,
    layout: InventoryLayout,
    iconUrl: (String) -> String,
    selected: ItemStack?,
    onItem: (ItemStack) -> Unit,
    allAction: Pair<String, () -> Unit>?,
    modifier: Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    var wornOpen by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PaneSpacing)) {
        // One row: tabs (the open one with its weight), the "all" action, and [trailing].
        Row(Modifier.height(PaneHeader), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(1f)) { ContainerTabs(tabs, shown, onOpen, iconUrl) }
            allAction?.takeIf { shown.stacks.isNotEmpty() }?.let { (label, onClick) -> AllButton(label, shown.id, onClick) }
            trailing?.let {
                Spacer(Modifier.width(8.dp)) // a little away from the "all" action: no mis-taps
                it()
            }
        }
        // Dropping a dragged item on the open container's items moves it there, like on its tab.
        val drag = LocalItemDrag.current
        val canDrop = drag != null && drag.active && drag.from != shown.id && !shown.container.locked
        Box(
            Modifier.fillMaxWidth().weight(1f).paneDropTarget(shown.id)
                .then(if (canDrop) Modifier.border(2.dp, if (drag?.target() == shown.id) Good else Good.copy(alpha = 0.35f), RoundedCornerShape(10.dp)) else Modifier),
        ) {
            when {
                shown.container.locked -> Hint("Locked")
                shown.stacks.isEmpty() -> Hint("Empty")
                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    // Whole rows only: the grid is cut to the rows that fit.
                    val itemHeight = if (layout == InventoryLayout.GRID) TileHeight else RowHeight
                    val rows = ((maxHeight + Gap) / (itemHeight + Gap)).toInt().coerceAtLeast(1)
                    val entries = remember(shown.stacks, wornOpen) { shown.stacks.foldWorn(wornOpen) }
                    LazyVerticalGrid(
                        // Compact grid: ~7 columns on the Thor's bottom screen. List: 2 columns of rows.
                        columns = GridCells.Adaptive(minSize = if (layout == InventoryLayout.GRID) 64.dp else 200.dp),
                        horizontalArrangement = Arrangement.spacedBy(Gap),
                        verticalArrangement = Arrangement.spacedBy(Gap),
                        modifier = Modifier.fillMaxWidth().height((itemHeight + Gap) * rows - Gap),
                    ) {
                        items(entries, key = { entry ->
                            when (entry) {
                                is PaneEntry.Stack -> entry.stack.first.id
                                is PaneEntry.Worn -> "worn"
                            }
                        }) { entry ->
                            when (entry) {
                                // Hold and drag onto a container tab or the other open container to move it (worn clothes stay put).
                                is PaneEntry.Stack -> Box(if (entry.worn) Modifier else Modifier.draggableItem(entry.stack, shown.id)) {
                                    if (layout == InventoryLayout.GRID) {
                                        GridTile(entry.stack, iconUrl, selected = entry.stack == selected, worn = entry.worn, onClick = { onItem(entry.stack) })
                                    } else {
                                        ListRow(entry.stack, iconUrl, selected = entry.stack == selected, worn = entry.worn, onClick = { onItem(entry.stack) })
                                    }
                                }
                                is PaneEntry.Worn -> WornTile(entry, iconUrl, list = layout == InventoryLayout.LIST, onClick = { wornOpen = !wornOpen })
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * "Put all" / "Take all": one mis-tap would move everything, so the first tap only arms it ("Put
 * all?", in red) and a second tap within 3 seconds does it.
 */
@Composable
private fun AllButton(label: String, containerId: String, onClick: () -> Unit) {
    var armed by remember(containerId) { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3_000)
            armed = false
        }
    }
    OutlinedButton(
        onClick = { if (armed) { armed = false; onClick() } else armed = true },
        contentPadding = PaddingValues(horizontal = 10.dp),
        modifier = Modifier.height(34.dp),
        colors = if (armed) ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) else ButtonDefaults.outlinedButtonColors(),
        border = if (armed) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else ButtonDefaults.outlinedButtonBorder(),
    ) {
        Text(if (armed) "$label?" else label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ContainerTabs(tabs: List<ContainerView>, shown: ContainerView, onOpen: (String) -> Unit, iconUrl: (String) -> String) {
    val scroll = rememberScrollState()
    val drag = LocalItemDrag.current
    Row(
        Modifier.fillMaxWidth()
            // More tabs than fit: the row fades out at the edge.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (scroll.canScrollForward) {
                    drawRect(
                        Brush.horizontalGradient(0.85f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                }
            }
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { view ->
            val open = view.id == shown.id
            // Only the open tab has its name; the others are the game's icon (and number, "Shelves 2").
            val compact = !open && view.container.icon != null
            val number = view.label.removePrefix(view.container.name).trim().takeIf { view.label != view.container.name }
            // While an item is dragged, the tabs it can go to light up.
            val canDrop = drag != null && drag.active && drag.from != view.id && !view.container.locked
            Surface(
                onClick = { onOpen(view.id) },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.semantics { contentDescription = view.label }.dropTarget(view.id),
                border = if (canDrop) BorderStroke(2.dp, if (drag?.target() == view.id) Good else Good.copy(alpha = 0.55f)) else null,
                color = if (open) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (open) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Column(Modifier.width(IntrinsicSize.Max)) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ContainerIcon(view.container, iconUrl, 20.dp)
                        if (compact) {
                            number?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                        } else {
                            Text(view.label, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
                        }
                        if (open) WeightLabel(view)
                    }
                    if (open) WeightMeter(view)
                }
            }
        }
    }
}

private fun ContainerView.overloaded() = weight != null && capacity != null && capacity > 0f && weight > capacity

/** "3.5/10" for the open tab; red when over capacity. */
@Composable
private fun WeightLabel(view: ContainerView) {
    val weight = view.weight ?: return
    val capacity = view.capacity?.takeIf { it > 0f }
    Text(
        if (capacity != null) "%.1f/%.0f".format(weight, capacity) else "%.1f".format(weight),
        style = MaterialTheme.typography.labelSmall,
        color = if (view.overloaded()) Danger else LocalContentColor.current.copy(alpha = 0.8f),
        maxLines = 1,
    )
}

/** A thin fill bar along the bottom of the open tab. */
@Composable
private fun WeightMeter(view: ContainerView) {
    val weight = view.weight ?: return
    val capacity = view.capacity?.takeIf { it > 0f } ?: return
    val fraction = (weight / capacity).coerceIn(0f, 1f)
    val color = if (view.overloaded()) Danger else MaterialTheme.colorScheme.primary
    // Drawn rather than a progress indicator: this must not widen the tab (an indicator asks for 240dp).
    Box(Modifier.fillMaxWidth().height(3.dp).drawBehind {
        drawRect(color, size = Size(size.width * fraction, size.height))
    })
}

@Composable
private fun Hint(text: String) {
    Text(text, Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun ContainerIcon(container: Container, iconUrl: (String) -> String, size: androidx.compose.ui.unit.Dp) {
    container.icon?.let { icon ->
        AsyncImage(
            model = iconUrl(icon),
            contentDescription = null,
            filterQuality = FilterQuality.None,
            modifier = Modifier.size(size),
        )
    }
}

/** Two small toggles showing the current modes; a tap switches to the other one. */
@Composable
internal fun DisplaySwitch(display: InventoryDisplay, onChange: (InventoryDisplay) -> Unit, showContainerLayout: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showContainerLayout) {
            val split = display.containers == ContainerLayout.SPLIT
            ToggleIcon(if (split) "Split layout" else "Single layout", onClick = {
                onChange(display.copy(containers = if (split) ContainerLayout.SINGLE else ContainerLayout.SPLIT))
            }) { color ->
                if (split) {
                    drawRect(color, Offset(0f, 0f), Size(size.width, size.height * 0.44f), style = Stroke(2.dp.toPx()))
                    drawRect(color, Offset(0f, size.height * 0.56f), Size(size.width, size.height * 0.44f), style = Stroke(2.dp.toPx()))
                } else {
                    drawRect(color, style = Stroke(2.dp.toPx()))
                }
            }
        }
        val grid = display.items == InventoryLayout.GRID
        ToggleIcon(if (grid) "Grid view" else "List view", onClick = {
            onChange(display.copy(items = if (grid) InventoryLayout.LIST else InventoryLayout.GRID))
        }) { color ->
            val cell = size.width * 0.42f
            if (grid) {
                for (x in listOf(0f, size.width - cell)) for (y in listOf(0f, size.height - cell)) {
                    drawRect(color, Offset(x, y), Size(cell, cell))
                }
            } else {
                for (i in 0..2) drawRect(color, Offset(0f, size.height * (0.08f + i * 0.36f)), Size(size.width, size.height * 0.16f))
            }
        }
    }
}

@Composable
private fun ToggleIcon(description: String, onClick: () -> Unit, draw: DrawScope.(Color) -> Unit) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(34.dp).semantics { contentDescription = description },
    ) {
        Canvas(Modifier.padding(9.dp).fillMaxSize()) { draw(color) }
    }
}
