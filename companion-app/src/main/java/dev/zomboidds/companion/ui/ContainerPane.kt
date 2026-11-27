package dev.zomboidds.companion.ui

import dev.zomboidds.companion.ItemSize
import androidx.compose.ui.unit.sp
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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import dev.zomboidds.companion.domain.key
import dev.zomboidds.companion.domain.paneEntries
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
    /** Items picked in this pane to act on together, by item id. */
    picked: Set<Long> = emptySet(),
    onHold: (ItemStack) -> Unit = {},
    onOpenPicked: () -> Unit = {},
    onClearPicked: () -> Unit = {},
    /** A box drawn over the items picked these (on top of what was picked). */
    onPickSet: (Set<Long>) -> Unit = {},
    /** Your worn clothes unfolded (held by the screen: the split layout sizes its panes by it). */
    wornOpen: Boolean = false,
    onWornOpenChange: (Boolean) -> Unit = {},
    /** Stacks shown item by item, by [ItemStack.key] (held by the screen, like [wornOpen]). */
    unfolded: Set<String> = emptySet(),
    onUnfoldedChange: (Set<String>) -> Unit = {},
    /**
     * Two slim header rows instead of one (side by side, where a pane is half as wide): the tabs
     * with [trailing], then the open container's name and weight with its action.
     */
    stackedHeader: Boolean = false,
) {
    // A whole stack counts once; from an unfolded one, each item picked.
    val pickedCount = shown.stacks.sumOf { stack -> stack.items.count { it.id in picked }.let { if (it == stack.count) 1 else it } }
    val boxSelect = remember(shown.id) { BoxSelect() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PaneSpacing)) {
        val actions = @Composable {
            if (pickedCount > 0) {
                // What's picked: its panel, or let go of all of it (instead of the "all" action).
                PickedChip("$pickedCount selected ›", filled = true, onClick = onOpenPicked)
                PickedChip("✕", filled = false, onClick = onClearPicked)
            } else {
                allAction?.takeIf { shown.stacks.isNotEmpty() }?.let { (label, onClick) -> AllButton(label, shown.id, onClick) }
            }
        }
        if (stackedHeader) {
            // [trailing] (the display buttons) at the end of the tab row: your own containers are few.
            Row(Modifier.height(PaneHeader), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)) { ContainerTabs(tabs, shown, onOpen, iconUrl, nameInTab = false) }
                trailing?.invoke()
            }
            Row(Modifier.height(PaneSubHeader), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // The name takes whatever the actions leave, and only shortens when that isn't enough.
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(shown.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    WeightLabel(shown)
                }
                actions()
            }
        } else {
            // One row: tabs (the open one with its weight), the "all" action, and [trailing].
            Row(Modifier.height(PaneHeader), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)) { ContainerTabs(tabs, shown, onOpen, iconUrl) }
                actions()
                trailing?.let {
                    Spacer(Modifier.width(8.dp)) // a little away from the "all" action: no mis-taps
                    it()
                }
            }
        }
        // Dropping a dragged item on the open container's items moves it there, like on its tab.
        val drag = LocalItemDrag.current
        val canDrop = drag != null && drag.active && drag.from != shown.id && !shown.container.locked
        Box(
            Modifier.fillMaxWidth().weight(1f).paneDropTarget(shown.id)
                .then(if (canDrop) Modifier.border(2.dp, if (drag?.target() == shown.id) Good else Good.copy(alpha = 0.35f), RoundedCornerShape(10.dp)) else Modifier)
                // From empty space: a box that picks what it touches.
                .then(if (shown.container.locked) Modifier else Modifier.boxSelect(boxSelect, picked, onPickSet, onEmptyTap = onClearPicked)),
        ) {
            when {
                shown.container.locked -> Hint("Locked")
                shown.stacks.isEmpty() -> Hint("Empty")
                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    // Whole rows only: the grid is cut to the rows that fit.
                    val itemHeight = if (layout == InventoryLayout.GRID) TileHeight else RowHeight
                    val rows = ((maxHeight + Gap) / (itemHeight + Gap)).toInt().coerceAtLeast(1)
                    val entries = remember(shown.stacks, wornOpen, unfolded) { shown.stacks.paneEntries(wornOpen, unfolded) }
                    LazyVerticalGrid(
                        // Compact grid: ~7 columns on the Thor's bottom screen. List: 2 columns of rows.
                        columns = GridCells.Adaptive(minSize = if (layout == InventoryLayout.GRID) TileMinWidth else 200.dp),
                        horizontalArrangement = Arrangement.spacedBy(Gap),
                        verticalArrangement = Arrangement.spacedBy(Gap),
                        modifier = Modifier.fillMaxWidth().height((itemHeight + Gap) * rows - Gap),
                    ) {
                        items(entries, key = { entry ->
                            when (entry) {
                                is PaneEntry.Stack -> if (entry.part) "part${entry.stack.first.id}" else entry.stack.first.id
                                is PaneEntry.Worn -> "worn"
                            }
                        }) { entry ->
                            when (entry) {
                                // Hold and drag onto a container tab or the other open container to move it; hold
                                // still to pick it (worn clothes stay put).
                                is PaneEntry.Stack -> Box(
                                    if (entry.worn) Modifier.boxSolid(boxSelect, entry.stack.first.id)
                                    else Modifier.draggableItem(
                                        entry.stack, shown.id,
                                        picked = if (entry.unfolded) entry.stack.items.all { it.id in picked } else entry.stack.items.any { it.id in picked },
                                        onTap = { onItem(entry.stack) },
                                    ) { onHold(entry.stack) }.boxTile(boxSelect, entry.stack, if (entry.part) "part${entry.stack.first.id}" else entry.stack.first.id),
                                ) {
                                    // An unfolded stack is picked once all of it is; its items show their own picks.
                                    val isPicked = if (entry.unfolded) entry.stack.items.all { it.id in picked } else entry.stack.items.any { it.id in picked }
                                    // Worn clothes can't be picked: while picking, a tap on them does nothing.
                                    val onClick = { if (!(entry.worn && pickedCount > 0)) onItem(entry.stack) }
                                    val fold = when {
                                        entry.part -> StackFold.PART
                                        entry.unfolded -> StackFold.OPEN
                                        else -> StackFold.NONE
                                    }
                                    val key = entry.stack.key
                                    val onFold = { onUnfoldedChange(if (key in unfolded) unfolded - key else unfolded + key) }.takeIf { !entry.worn }
                                    if (layout == InventoryLayout.GRID) {
                                        GridTile(entry.stack, iconUrl, selected = entry.stack == selected, worn = entry.worn, onClick = onClick, picked = isPicked,
                                            fold = fold, onFold = onFold)
                                    } else {
                                        ListRow(entry.stack, iconUrl, selected = entry.stack == selected, worn = entry.worn, onClick = onClick, picked = isPicked,
                                            fold = fold, onFold = onFold)
                                    }
                                }
                                is PaneEntry.Worn -> Box(Modifier.boxSolid(boxSelect, "worn")) {
                                    WornTile(entry, iconUrl, list = layout == InventoryLayout.LIST, onClick = { onWornOpenChange(!wornOpen) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickedChip(label: String, filled: Boolean, onClick: () -> Unit) {
    val padding = PaddingValues(horizontal = 10.dp)
    if (filled) {
        Button(onClick = onClick, contentPadding = padding, modifier = Modifier.height(34.dp)) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, contentPadding = padding, modifier = Modifier.height(34.dp)) { Text(label) }
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
private fun ContainerTabs(
    tabs: List<ContainerView>,
    shown: ContainerView,
    onOpen: (String) -> Unit,
    iconUrl: (String) -> String,
    /** The open tab shows its name and weight; without, they're in the row below and the tab is its icon. */
    nameInTab: Boolean = true,
) {
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
            val compact = (!open || !nameInTab) && view.container.icon != null
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
                        if (open && nameInTab) WeightLabel(view)
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

/**
 * A thin gauge along the bottom of the open tab: a faint track the whole width, filled as far as
 * the container is. Without the track a nearly empty container's few pixels of fill, cut by the
 * tab's rounded corner, looked like a stray mark.
 */
@Composable
private fun WeightMeter(view: ContainerView) {
    val weight = view.weight ?: return
    val capacity = view.capacity?.takeIf { it > 0f } ?: return
    val fraction = (weight / capacity).coerceIn(0f, 1f)
    FillMeter(fraction, if (view.overloaded()) Danger else MaterialTheme.colorScheme.primary)
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

/**
 * One View button for how the inventory shows: container layout, grid or list, and item size. A tap
 * opens a card with all of them as little pictures; a choice applies at once and the card stays open,
 * so several can be changed in one go. One button leaves the tab row's room to the bags.
 */
@Composable
internal fun DisplaySwitch(display: InventoryDisplay, onChange: (InventoryDisplay) -> Unit, showContainerLayout: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(8.dp),
            color = if (open) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            border = if (open) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier.size(34.dp).semantics { contentDescription = "View" },
        ) {
            // The button shows the container layout, or grid/list where there's only one pane.
            Box(contentAlignment = Alignment.Center) {
                if (showContainerLayout) LayoutGlyph(display.containers.parts, Size(17f, 13f))
                else ItemsGlyph(display.items, 16.dp)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, properties = NoFocusMenu) {
            Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (showContainerLayout) {
                    PickerSection("Containers") {
                        ContainerLayout.entries.forEach { option ->
                            PickerOption(option.label, option == display.containers, { onChange(display.copy(containers = option)) }) {
                                LayoutGlyph(option.parts, Size(40f, 28f))
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PickerSection("Items") {
                        InventoryLayout.entries.forEach { option ->
                            PickerOption(if (option == InventoryLayout.GRID) "Grid" else "List", option == display.items,
                                { onChange(display.copy(items = option)) }) { ItemsGlyph(option, 22.dp) }
                        }
                    }
                    PickerSection("Size") {
                        ItemSize.entries.forEach { option ->
                            PickerOption(if (option == ItemSize.LARGE) "Large" else "Normal", option == display.size,
                                { onChange(display.copy(size = option)) }) {
                                Text("A", fontSize = if (option == ItemSize.LARGE) 24.sp else 14.sp,
                                    lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerSection(title: String, options: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { options() }
    }
}

/** Four squares for the grid, three bars for the list. */
@Composable
private fun ItemsGlyph(layout: InventoryLayout, size: androidx.compose.ui.unit.Dp) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(size)) {
        val cell = this.size.width * 0.42f
        if (layout == InventoryLayout.GRID) {
            for (x in listOf(0f, this.size.width - cell)) for (y in listOf(0f, this.size.height - cell)) {
                drawRect(color, Offset(x, y), Size(cell, cell))
            }
        } else {
            for (i in 0..2) drawRect(color, Offset(0f, this.size.height * (0.08f + i * 0.36f)), Size(this.size.width, this.size.height * 0.16f))
        }
    }
}

private val ContainerLayout.label get() = when (this) {
    ContainerLayout.SPLIT -> "Top and bottom"
    ContainerLayout.SIDE_BY_SIDE -> "Side by side"
    ContainerLayout.SINGLE -> "One at a time"
}

/** The panes, for the picker's pictures. */
private val ContainerLayout.parts get() = when (this) {
    ContainerLayout.SPLIT -> listOf(Rect(0f, 0f, 1f, 0.42f), Rect(0f, 0.58f, 1f, 1f))
    ContainerLayout.SIDE_BY_SIDE -> listOf(Rect(0f, 0f, 0.42f, 1f), Rect(0.58f, 0f, 1f, 1f))
    ContainerLayout.SINGLE -> listOf(Rect(0f, 0f, 1f, 1f))
}
