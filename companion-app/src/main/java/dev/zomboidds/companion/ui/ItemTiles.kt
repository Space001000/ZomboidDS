package dev.zomboidds.companion.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.zomboidds.companion.domain.CookState
import dev.zomboidds.companion.domain.EquipSlot
import dev.zomboidds.companion.domain.Freshness
import dev.zomboidds.companion.domain.InventoryItem
import dev.zomboidds.companion.domain.ItemStack
import dev.zomboidds.companion.domain.PaneEntry
import dev.zomboidds.companion.domain.isKeyRing
import dev.zomboidds.companion.domain.stacks
import dev.zomboidds.companion.domain.tileName

// How items look in the inventory: grid tiles, list rows, the folded worn clothes, and their markers.
/**
 * Compact tile: icon first, the name on one line; everything else is in the action panel. In hand:
 * a small corner label. Condition: a thin bar only once it's damaged. [worn]: one of the unfolded
 * worn clothes. [picked]: one of several picked to act on together (a check in the corner).
 */
@Composable
internal fun GridTile(
    stack: ItemStack,
    iconUrl: (String) -> String,
    selected: Boolean,
    worn: Boolean,
    onClick: () -> Unit,
    picked: Boolean = false,
    fold: StackFold = StackFold.NONE,
    onFold: (() -> Unit)? = null,
) {
    val item = stack.first
    Card(onClick = onClick, border = selectedBorder(selected || picked) ?: wornBorder(worn) ?: foldBorder(fold),
        colors = foldColors(fold), modifier = Modifier.height(TileHeight)) {
        Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(3.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Box(Modifier.fillMaxWidth().height(38.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.alpha(if (item.unwanted) UnwantedAlpha else 1f)) {
                        ItemIcon(item, iconUrl, 36.dp)
                        if (item.read) ReadTick(iconUrl, Modifier.align(Alignment.BottomStart))
                    }
                    if (stack.count > 1) StackBadge(stack.count, fold, onFold, Modifier.align(Alignment.BottomEnd), 9.sp)
                }
                Text(
                    if (item.isKeyRing) "Keys" else item.tileName,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.unwanted) LocalContentColor.current.copy(alpha = UnwantedAlpha) else Color.Unspecified,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val hand = handLabel(item.equipped).takeIf { !picked }
            // Cooked, Uncooked, Burnt: the game's word, where a held item says "Main".
            val cooked = item.cooking?.text?.takeIf { hand == null && !picked }
            hand?.let { CornerLabel(it, Modifier.align(Alignment.TopStart)) }
            cooked?.let { CornerLabel(it, Modifier.align(Alignment.TopStart), cookColor(item.cooking.state)) }
            if (picked) PickedCheck(Modifier.align(Alignment.TopStart))
            item.freshness?.let {
                FreshnessDot(it, Modifier.align(if (hand == null && cooked == null) Alignment.TopStart else Alignment.TopEnd).padding(3.dp))
            }
            damage(item.condition)?.let { (fraction, color) ->
                Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 3.dp)) { Meter(fraction, color, 2.dp) }
            }
        }
        // Along the bottom edge: the cooking bar while it heats, otherwise how full it is.
        val heating = item.cooking?.progress
        when {
            heating != null -> FillMeter(heating, if (item.cooking.burning) Danger else Good, Modifier.align(Alignment.BottomCenter))
            item.fluid != null -> FillMeter(item.fluid.fraction, MaterialTheme.colorScheme.primary, Modifier.align(Alignment.BottomCenter))
        }
        }
    }
}

/**
 * A thin gauge: a faint track the whole width, filled as far as [fraction]. The open container
 * tab's weight, a bottle's fill and the cooking bar all use it. Drawn rather than a progress
 * indicator: this must not widen what it's in (an indicator asks for 240dp).
 */
@Composable
internal fun FillMeter(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val track = LocalContentColor.current.copy(alpha = 0.18f)
    Box(modifier.fillMaxWidth().height(3.dp).drawBehind {
        drawRect(track)
        drawRect(color, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height))
    })
}

/** Burnt in red, uncooked in yellow; cooked is the norm. */
private fun cookColor(state: CookState?) = when (state) {
    CookState.BURNT -> Danger
    CookState.UNCOOKED -> Caution
    else -> null
}

private fun freshColor(freshness: Freshness) = when (freshness) {
    Freshness.FRESH -> Good
    Freshness.STALE -> Caution
    Freshness.ROTTEN -> Danger
}

/**
 * The game's name with its state words coloured like the tile's dot and corner label:
 * "Bread (Stale)", "Chicken (Fresh, Uncooked)".
 */
internal fun itemNameText(item: InventoryItem): AnnotatedString = buildAnnotatedString {
    append(item.name)
    val bracket = item.name.lastIndexOf('(').coerceAtLeast(0)
    fun mark(word: String?, color: Color?) {
        if (word.isNullOrEmpty() || color == null) return
        val at = item.name.indexOf(word, bracket)
        if (at >= 0) addStyle(SpanStyle(color = color), at, at + word.length)
    }
    mark(item.freshnessText, item.freshness?.let(::freshColor))
    mark(item.cooking?.text, cookColor(item.cooking?.state))
}

/** Your worn clothes folded into one tile (or row): a few of their icons and how many. */
@Composable
internal fun WornTile(entry: PaneEntry.Worn, iconUrl: (String) -> String, list: Boolean, onClick: () -> Unit) {
    val label = "Worn ×${entry.stacks.size}"
    val icons = @Composable {
        Box(Modifier.size(width = 40.dp, height = 36.dp)) {
            entry.stacks.take(3).forEachIndexed { i, stack ->
                Box(Modifier.offset(x = (i * 8).dp, y = (i * 5).dp)) { ItemIcon(stack.first, iconUrl, 24.dp) }
            }
        }
    }
    Card(
        onClick = onClick,
        border = BorderStroke(1.dp, if (entry.open) Caution else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.height(if (list) RowHeight else TileHeight),
    ) {
        if (list) {
            Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                icons()
                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(if (entry.open) "▴" else "▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.height(38.dp), contentAlignment = Alignment.Center) { icons() }
                Text(label + if (entry.open) " ▴" else "", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun wornBorder(worn: Boolean) = if (worn) BorderStroke(1.dp, Caution.copy(alpha = 0.5f)) else null

/** A stack unfolded like the game's inventory does: the stack ([OPEN]) and each of its items ([PART]). */
internal enum class StackFold { NONE, OPEN, PART }

/** The open stack is outlined like the open Worn tile; its items sit a shade darker, like the worn clothes. */
@Composable
private fun foldBorder(fold: StackFold) = when (fold) {
    StackFold.OPEN -> BorderStroke(1.dp, Caution)
    StackFold.PART -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    StackFold.NONE -> null
}

@Composable
private fun foldColors(fold: StackFold) =
    if (fold == StackFold.PART) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) else CardDefaults.cardColors()

/**
 * "×5": how many are stacked. A tap on it unfolds the stack into its items, and folds it again
 * ("×5 ▴"); the area around it is a finger's size.
 */
@Composable
private fun StackBadge(count: Int, fold: StackFold, onFold: (() -> Unit)?, modifier: Modifier, fontSize: TextUnit) {
    val text = if (fold == StackFold.OPEN) "×$count ▴" else "×$count"
    if (onFold == null) {
        Badge(text, modifier, fontSize = fontSize)
        return
    }
    Box(
        modifier.size(34.dp).clickable(interactionSource = null, indication = null, onClick = onFold)
            .semantics { contentDescription = if (fold == StackFold.OPEN) "Fold the stack" else "Unfold the stack" },
        contentAlignment = Alignment.BottomEnd,
    ) {
        Badge(text, Modifier, fontSize = fontSize)
    }
}

/** Only damaged items get a bar: green, yellow below 60 %, red below 30 %. */
internal fun damage(condition: Float?): Pair<Float, Color>? {
    if (condition == null || condition >= 0.995f) return null
    return condition to when {
        condition < 0.3f -> Danger
        condition < 0.6f -> Caution
        else -> Good
    }
}

/** List row: readable names plus where the item is and what it weighs. */
@Composable
internal fun ListRow(
    stack: ItemStack,
    iconUrl: (String) -> String,
    selected: Boolean,
    worn: Boolean,
    onClick: () -> Unit,
    picked: Boolean = false,
    fold: StackFold = StackFold.NONE,
    onFold: (() -> Unit)? = null,
) {
    val item = stack.first
    Card(onClick = onClick, border = selectedBorder(selected || picked) ?: wornBorder(worn) ?: foldBorder(fold),
        colors = foldColors(fold), modifier = Modifier.height(RowHeight)) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (picked) PickedCheck(Modifier)
            Box(Modifier.alpha(if (item.unwanted) UnwantedAlpha else 1f)) {
                ItemIcon(item, iconUrl, 32.dp)
                if (item.read) ReadTick(iconUrl, Modifier.align(Alignment.BottomStart))
            }
            Column(Modifier.weight(1f)) {
                // The game's own name: its words say how fresh and how cooked, in colour.
                Text(itemNameText(item), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (item.unwanted) LocalContentColor.current.copy(alpha = UnwantedAlpha) else Color.Unspecified)
                val heating = item.cooking?.progress
                val details = listOfNotNull(
                    equippedLabel(item.equipped),
                    item.weight?.let { "%.1f".format(it * stack.count) },
                    damage(item.condition)?.let { "${(it.first * 100).toInt()}%" }, // 100 % is the norm: not shown
                    item.fluid?.amountText(),
                    heating?.let { if (item.cooking.burning) "Burning" else "Cooking" },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(details.joinToString(" · "), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    when {
                        heating != null -> FillMeter(heating, if (item.cooking.burning) Danger else Good, Modifier.width(40.dp))
                        item.fluid != null -> FillMeter(item.fluid.fraction, MaterialTheme.colorScheme.primary, Modifier.width(40.dp))
                    }
                }
            }
            if (stack.count > 1) StackBadge(stack.count, fold, onFold, Modifier, 12.sp)
        }
    }
}

@Composable
internal fun ItemIcon(item: InventoryItem, iconUrl: (String) -> String, size: androidx.compose.ui.unit.Dp) {
    item.icon?.let { icon ->
        AsyncImage(
            model = iconUrl(icon),
            contentDescription = item.name,
            filterQuality = FilterQuality.None, // pixel art: keep it crisp when scaled up
            modifier = Modifier.size(size),
        )
    }
}

@Composable
internal fun selectedBorder(selected: Boolean) =
    if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null

@Composable
internal fun Meter(fraction: Float, color: Color, height: androidx.compose.ui.unit.Dp) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(height),
        color = color,
        drawStopIndicator = {},
        gapSize = 0.dp,
    )
}

/** In hand, for the tile's corner (worn clothes are grouped instead). */
private fun handLabel(slot: EquipSlot?) = equippedLabel(slot).takeIf { slot != EquipSlot.WORN }

internal fun equippedLabel(slot: EquipSlot?) = when (slot) {
    EquipSlot.PRIMARY -> "Main"
    EquipSlot.SECONDARY -> "Off"
    EquipSlot.BOTH -> "Both"
    EquipSlot.WORN -> "Worn"
    null -> null
}

/** The check on a picked item. */
@Composable
private fun PickedCheck(modifier: Modifier) {
    Box(
        modifier.size(16.dp).background(MaterialTheme.colorScheme.primary, CircleShape).semantics { contentDescription = "picked" },
        contentAlignment = Alignment.Center,
    ) {
        Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun Badge(text: String, modifier: Modifier, fontSize: TextUnit = 9.sp) {
    Text(
        text,
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
            .padding(horizontal = 3.dp),
        color = MaterialTheme.colorScheme.onPrimary,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

/** A quiet label in a tile's corner ("Main"): readable, without covering the icon like a badge. */
@Composable
private fun CornerLabel(text: String, modifier: Modifier, color: Color? = null) {
    Text(
        text,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
            .padding(horizontal = 3.dp),
        color = color ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        fontSize = 8.sp,
        maxLines = 1,
    )
}

/** How far an item set Unwanted fades, like the game's grey for it. */
private const val UnwantedAlpha = 0.5f

/** Read, watched or heard already: the game's own tick, on the icon's corner. */
@Composable
private fun ReadTick(iconUrl: (String) -> String, modifier: Modifier) {
    AsyncImage(
        model = iconUrl("Tick_Mark-10"),
        contentDescription = "read",
        filterQuality = FilterQuality.None,
        modifier = modifier.size(14.dp),
    )
}

/** Food on a tile: green fresh, yellow stale, red rotten (in the list view the name's word is coloured instead). */
@Composable
private fun FreshnessDot(freshness: Freshness, modifier: Modifier) {
    Box(modifier.size(8.dp).background(freshColor(freshness), CircleShape).semantics { contentDescription = freshness.name.lowercase() })
}
