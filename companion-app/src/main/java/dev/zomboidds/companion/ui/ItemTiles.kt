package dev.zomboidds.companion.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.zomboidds.companion.domain.EquipSlot
import dev.zomboidds.companion.domain.Freshness
import dev.zomboidds.companion.domain.InventoryItem
import dev.zomboidds.companion.domain.ItemStack
import dev.zomboidds.companion.domain.PaneEntry
import dev.zomboidds.companion.domain.isKeyRing
import dev.zomboidds.companion.domain.stacks

// How items look in the inventory: grid tiles, list rows, the folded worn clothes, and their markers.
/**
 * Compact tile: icon first, the name on one line; everything else is in the action panel. In hand:
 * a small corner label. Condition: a thin bar only once it's damaged. [worn]: one of the unfolded
 * worn clothes.
 */
@Composable
internal fun GridTile(stack: ItemStack, iconUrl: (String) -> String, selected: Boolean, worn: Boolean, onClick: () -> Unit) {
    val item = stack.first
    Card(onClick = onClick, border = selectedBorder(selected) ?: wornBorder(worn), modifier = Modifier.height(TileHeight)) {
        Box(Modifier.fillMaxSize().padding(3.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Box(Modifier.fillMaxWidth().height(38.dp), contentAlignment = Alignment.Center) {
                    ItemIcon(item, iconUrl, 36.dp)
                    if (stack.count > 1) Badge("×${stack.count}", Modifier.align(Alignment.BottomEnd))
                }
                Text(
                    if (item.isKeyRing) "Keys" else item.name,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val hand = handLabel(item.equipped)
            hand?.let { CornerLabel(it, Modifier.align(Alignment.TopStart)) }
            item.freshness?.let { FreshnessDot(it, Modifier.align(if (hand == null) Alignment.TopStart else Alignment.TopEnd).padding(3.dp)) }
            damage(item.condition)?.let { (fraction, color) ->
                Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 3.dp)) { Meter(fraction, color, 2.dp) }
            }
        }
    }
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
private fun wornBorder(worn: Boolean) = if (worn) BorderStroke(1.dp, Caution.copy(alpha = 0.5f)) else null

/** Only damaged items get a bar: green, yellow below 60 %, red below 30 %. */
private fun damage(condition: Float?): Pair<Float, Color>? {
    if (condition == null || condition >= 0.995f) return null
    return condition to when {
        condition < 0.3f -> Danger
        condition < 0.6f -> Caution
        else -> Good
    }
}

/** List row: readable names plus where the item is and what it weighs. */
@Composable
internal fun ListRow(stack: ItemStack, iconUrl: (String) -> String, selected: Boolean, worn: Boolean, onClick: () -> Unit) {
    val item = stack.first
    Card(onClick = onClick, border = selectedBorder(selected) ?: wornBorder(worn), modifier = Modifier.height(RowHeight)) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ItemIcon(item, iconUrl, 32.dp)
            item.freshness?.let { FreshnessDot(it, Modifier) }
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val details = listOfNotNull(
                    equippedLabel(item.equipped),
                    item.weight?.let { "%.1f".format(it * stack.count) },
                    damage(item.condition)?.let { "${(it.first * 100).toInt()}%" }, // 100 % is the norm: not shown
                )
                Text(details.joinToString(" · "), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (stack.count > 1) Badge("×${stack.count}", Modifier, fontSize = 12.sp)
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
private fun selectedBorder(selected: Boolean) =
    if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null

@Composable
private fun Meter(fraction: Float, color: Color, height: androidx.compose.ui.unit.Dp) {
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

@Composable
private fun Badge(text: String, modifier: Modifier, fontSize: TextUnit = 9.sp) {
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
private fun CornerLabel(text: String, modifier: Modifier) {
    Text(
        text,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
            .padding(horizontal = 3.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        fontSize = 8.sp,
        maxLines = 1,
    )
}

/** Food: green fresh, yellow stale, red rotten (the game's name says it in words: "Stale Bread"). */
@Composable
private fun FreshnessDot(freshness: Freshness, modifier: Modifier) {
    val color = when (freshness) {
        Freshness.FRESH -> Good
        Freshness.STALE -> Caution
        Freshness.ROTTEN -> Danger
    }
    Box(modifier.size(8.dp).background(color, CircleShape).semantics { contentDescription = freshness.name.lowercase() })
}
