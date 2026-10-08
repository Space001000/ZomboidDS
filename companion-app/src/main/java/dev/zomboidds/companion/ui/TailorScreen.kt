package dev.zomboidds.companion.ui

import androidx.compose.foundation.background
import dev.zomboidds.companion.domain.ItemMenuResult
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.DpOffset
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.Fetched
import dev.zomboidds.companion.domain.Garment
import dev.zomboidds.companion.domain.GarmentPart
import dev.zomboidds.companion.domain.GarmentSummary
import dev.zomboidds.companion.domain.ItemActions
import dev.zomboidds.companion.domain.ItemMenu
import dev.zomboidds.companion.domain.MenuOption
import dev.zomboidds.companion.domain.SewingKit
import dev.zomboidds.companion.domain.TailorList
import dev.zomboidds.companion.domain.Tailoring
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Which clothes the Tailor grid shows. */
private enum class Shown(val label: String) { ALL("All"), WORN("Worn"), BAGS("In bags") }

/**
 * Tailor: the player's clothes as a grid, worn first, with their holes and patches, and the sewing
 * kit the game patches with. Tap one for its garment panel (the game's Inspect window).
 * [changes]: anything that changes the clothes or the kit (inventory, containers); the list follows it.
 */
@Composable
fun TailorScreen(tailoring: Tailoring, actions: ItemActions, iconUrl: (String) -> String, changes: Any?) {
    var list by remember { mutableStateOf<Fetched<TailorList>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(changes, reload) {
        if (list != null) delay(500) // several changes in a row (a patch uses thread and fabric): ask once
        list = tailoring.tailorList()
    }
    var holesOnly by rememberSaveable { mutableStateOf(false) }
    var shown by rememberSaveable { mutableStateOf(Shown.ALL) }
    var selected by remember { mutableStateOf<Long?>(null) }

    Box(Modifier.fillMaxSize()) {
        when (val result = list) {
            null -> LoadingRow("Loading your clothes...")
            is Fetched.Failed -> FailedRow(result.reason, "Retry") { reload++ }
            is Fetched.Ready -> {
                val garments = result.value.garments
                val visible = garments.filter {
                    (!holesOnly || it.holes > 0) && when (shown) {
                        Shown.ALL -> true
                        Shown.WORN -> it.worn
                        Shown.BAGS -> !it.worn
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(PaneSpacing)) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = holesOnly, onClick = { holesOnly = !holesOnly }, label = { Text("With holes") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Good.copy(alpha = 0.25f)),
                        )
                        Shown.entries.forEach { option ->
                            val count = garments.count { option == Shown.ALL || (option == Shown.WORN) == it.worn }
                            FilterChip(selected = shown == option, onClick = { shown = option }, label = { Text("${option.label}  $count") })
                        }
                    }
                    KitLine(result.value.kit, result.value.tailoring, iconUrl)
                    if (visible.isEmpty()) {
                        Text(
                            if (holesOnly) "No holes in these clothes." else "No clothes here.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 64.dp),
                        horizontalArrangement = Arrangement.spacedBy(Gap),
                        verticalArrangement = Arrangement.spacedBy(Gap),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(visible, key = { it.id }) { garment ->
                            GarmentTile(garment, iconUrl, selected = garment.id == selected, onClick = { selected = garment.id })
                        }
                    }
                }
            }
        }
        selected?.let { id ->
            GarmentPanel(tailoring, actions, id, iconUrl, changes, onClose = { selected = null })
        }
    }
}

/**
 * What the game's menu sews with: needle and thread (✓, or a red ✗), the three fabrics with how many
 * you have (greyed at 0), and your Tailoring level.
 */
@Composable
private fun KitLine(kit: SewingKit, tailoring: Int?, iconUrl: (String) -> String) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        @Composable
        fun Part(icon: String?, text: String, color: Color = muted, dim: Boolean = false) {
            Row(Modifier.alpha(if (dim) 0.45f else 1f), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                GameIcon(icon, iconUrl, 18.dp)
                Text(text, color = color, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
        Part("Item_Needle", if (kit.needle) "✓" else "✗", if (kit.needle) muted else Danger)
        Part("Item_Thread", if (kit.thread) "✓" else "✗", if (kit.thread) muted else Danger)
        kit.fabrics.forEach { Part(it.icon, "${it.name} ${it.count}", dim = it.count == 0) }
        tailoring?.let { Text("Tailoring $it", color = muted, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
    }
}

/**
 * A garment like an inventory tile (worn ones edged like the inventory's): the holes in a red
 * corner badge, "patched" when it has patches and no holes, ✕ when the game can't repair it, and
 * its condition along the bottom.
 */
@Composable
private fun GarmentTile(garment: GarmentSummary, iconUrl: (String) -> String, selected: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, border = selectedBorder(selected) ?: wornBorder(garment.worn), modifier = Modifier.height(TileHeight)) {
        Box(Modifier.fillMaxSize().padding(3.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Box(Modifier.fillMaxWidth().height(38.dp), contentAlignment = Alignment.Center) {
                    GameIcon(garment.icon, iconUrl, 36.dp)
                }
                Text(garment.name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            if (garment.holes > 0) {
                Badge(if (garment.holes == 1) "1 hole" else "${garment.holes} holes", Danger.copy(alpha = 0.85f), Color.White,
                    Modifier.align(Alignment.TopEnd))
            }
            when {
                !garment.repairable -> Badge("✕", Color.Black.copy(alpha = 0.55f), MaterialTheme.colorScheme.onSurfaceVariant,
                    Modifier.align(Alignment.TopStart))
                garment.patches > 0 && garment.holes == 0 -> Badge("patched", Color.Black.copy(alpha = 0.55f),
                    MaterialTheme.colorScheme.primary, Modifier.align(Alignment.TopStart))
            }
            damage(garment.condition)?.let { (fraction, color) ->
                Box(Modifier.align(Alignment.BottomCenter)) { Meter(fraction, color, 2.dp) }
            }
        }
    }
}

@Composable
private fun Badge(text: String, background: Color, color: Color, modifier: Modifier) {
    Text(
        text, modifier.background(background, RoundedCornerShape(4.dp)).padding(horizontal = 3.dp),
        fontSize = 9.sp, lineHeight = 13.sp, color = color, maxLines = 1,
    )
}

/**
 * One garment as the game's Inspect window shows it: condition, blood and dirt, the body parts it
 * covers lit on the silhouette (red: a hole, green: patched), and a row per part with its defence,
 * hole, blood and patch. Tap a part for the window's own menu for it (patch, pad, unpatch). While
 * the game sews, that part's row shows its progress bar. Asks the game again every second, faster
 * while it sews, and when [changes] says the clothes or kit changed.
 */
@Composable
internal fun BoxScope.GarmentPanel(
    tailoring: Tailoring,
    actions: ItemActions,
    itemId: Long,
    iconUrl: (String) -> String,
    changes: Any?,
    onClose: () -> Unit,
) {
    var garment by remember(itemId) { mutableStateOf<Fetched<Garment>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(itemId, changes, reload) {
        while (true) {
            val fetched = tailoring.garment(itemId)
            // Keep showing the last good read through a hiccup (the garment is moving into the inventory).
            if (fetched is Fetched.Ready || garment !is Fetched.Ready) garment = fetched
            val sewing = (fetched as? Fetched.Ready)?.value?.sewing != null
            delay(if (sewing) 400 else 1_000)
        }
    }
    var part by remember(itemId) { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        if (message != null) {
            delay(4_000)
            message = null
        }
    }
    val scope = rememberCoroutineScope()

    BottomPanel(onDismiss = onClose) {
        when (val result = garment) {
            null -> LoadingRow("Loading the garment...")
            is Fetched.Failed -> FailedRow(result.reason, "Close", onClose)
            is Fetched.Ready -> {
                val g = result.value
                GarmentHeader(g, iconUrl, onClose)
                GarmentBars(g)
                message?.let { Text(it, color = ErrorText, style = MaterialTheme.typography.bodySmall) }
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GarmentSilhouette(g, iconUrl, Modifier.width(88.dp).height(216.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        g.parts.forEach { p ->
                            val open = p.id == part
                            // While the game sews a part, its row shows the bar; nothing to choose.
                            Box {
                                PartRow(p, open, onClick = { if (p.sewing == null) part = if (open) null else p.id })
                                if (open) {
                                    PartPopout(
                                        load = { tailoring.garmentMenu(g.id, p.id) },
                                        key = Triple(g.id, p.id, p.hole to p.patch),
                                        cantRepair = g.cantRepair,
                                        iconUrl = iconUrl,
                                        onSelect = { menuId, optionId ->
                                            part = null
                                            scope.launch {
                                                when (val chosen = actions.selectMenuOption(menuId, optionId)) {
                                                    CommandResult.Ok -> reload++
                                                    is CommandResult.Failed -> message = chosen.reason
                                                }
                                            }
                                        },
                                        onDismiss = { part = null },
                                    )
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
private fun GarmentHeader(garment: Garment, iconUrl: (String) -> String, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GameIcon(garment.icon, iconUrl, 44.dp)
        Column(Modifier.weight(1f)) {
            Text(garment.name, style = MaterialTheme.typography.titleMedium)
            val facts = listOfNotNull("Worn".takeIf { garment.worn }, garment.tailoring?.let { "Tailoring $it" })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (facts.isNotEmpty()) {
                    Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                garment.cantRepair?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Danger) }
            }
        }
        TextButton(onClick = onClose) { Text("Close") }
    }
}

/** The window's three bars: Condition (green when high), Overall Bloodiness and Dirtiness (red when high). */
@Composable
private fun GarmentBars(garment: Garment) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        @Composable
        fun Bar(label: String, value: Float, highIsGood: Boolean) {
            Column(Modifier.weight(1f)) {
                Row {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val bad = if (highIsGood) 1f - value else value
                FillMeter(value, lerpColor(Good, Danger, bad), Modifier.padding(top = 2.dp))
            }
        }
        garment.condition?.let { Bar("Condition", it, highIsGood = true) }
        Bar("Overall Bloodiness", garment.blood, highIsGood = false)
        Bar("Overall Dirtiness", garment.dirt, highIsGood = false)
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))

/** The body with the garment's parts lit: a hole red, a patch green, the rest of what it covers light. */
@Composable
private fun GarmentSilhouette(garment: Garment, iconUrl: (String) -> String, modifier: Modifier) {
    val byId = garment.parts.associateBy { it.id }
    // The game draws Back on the abdomen; the abdomen's own part (Lower Torso) wins when it has one.
    val back = byId["Back"]
    BodySilhouette(female = false, iconUrl = iconUrl, modifier = modifier) { id ->
        val p = byId[id] ?: back?.takeIf { id == "Torso_Lower" }
        when {
            p == null -> Color(0xFF2C2722)
            p.hole -> Danger
            p.patch != null -> Good
            else -> Color(0xFF8A7A69)
        }
    }
}

/** A part: its name, Bite and Scratch (and Bullet when it has any), then hole, blood and patch, or the sewing bar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PartRow(part: GarmentPart, open: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (open) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f) else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(part.name, Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "Bite ${part.bite} · Scratch ${part.scratch}" + if (part.bullet > 0) " · Bullet ${part.bullet}" else "",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
        )
        val sewing = part.sewing
        if (sewing != null) {
            Box(Modifier.weight(1f).height(16.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), RoundedCornerShape(2.dp))) {
                Box(Modifier.fillMaxWidth(sewing.progress.coerceIn(0f, 1f)).height(16.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), RoundedCornerShape(2.dp)))
                Text(sewing.name, Modifier.padding(start = 6.dp), fontSize = 11.sp, lineHeight = 16.sp)
            }
        } else {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (part.hole) Tag("Hole", Danger.copy(alpha = 0.22f), Color(0xFFFF9D93))
                part.blood?.let { Tag("Blood ${(it * 100).toInt()}%", Danger.copy(alpha = 0.12f), Color(0xFFE9A59D)) }
                part.patch?.let { Tag(it, Good.copy(alpha = 0.16f), Color(0xFF9FE39F)) }
            }
        }
    }
}

@Composable
private fun Tag(text: String, background: Color, color: Color) {
    Text(text, Modifier.background(background, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp),
        fontSize = 11.sp, lineHeight = 16.sp, color = color, maxLines = 1)
}

/**
 * The window's menu for a part as a popout from its row, like the game's right-click menu (and like
 * Craft ▾'s menu: a panel for a thing, a popout for a choice in it). A submenu (Patch Hole, Add
 * Padding) is a heading over its choices; every row does one thing: its fabric's icon, its name and,
 * under it, the game's tooltip (what it adds at your Tailoring level, or why it's greyed). Tap
 * outside to close. Not focusable: the controller stays with the game.
 */
@Composable
private fun PartPopout(
    load: suspend () -> ItemMenuResult,
    key: Any,
    cantRepair: String?,
    iconUrl: (String) -> String,
    onSelect: (menuId: String, optionId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val menu by produceState<ItemMenuResult?>(null, key) { value = if (cantRepair != null) null else load() }
    DropdownMenu(
        expanded = true, onDismissRequest = onDismiss, properties = NoFocusMenu,
        offset = DpOffset(24.dp, 4.dp), modifier = Modifier.widthIn(min = 260.dp, max = 380.dp),
    ) {
        when (val result = menu) {
            null if cantRepair != null -> PopoutNote(cantRepair)
            null -> PopoutNote("Loading the game's menu...")
            is ItemMenuResult.Failed -> PopoutNote(result.reason)
            is ItemMenuResult.Ready -> {
                val options = result.menu.options
                val (groups, single) = options.partition { it.children.isNotEmpty() }
                groups.forEachIndexed { i, group ->
                    if (i > 0) HorizontalDivider()
                    Text(group.name.uppercase(), Modifier.padding(start = 14.dp, top = 8.dp, bottom = 2.dp),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    group.children.forEach { PopoutItem(it, iconUrl) { onSelect(result.menu.menuId, it.id) } }
                }
                if (groups.isNotEmpty() && single.isNotEmpty()) HorizontalDivider()
                single.forEach { PopoutItem(it, iconUrl) { onSelect(result.menu.menuId, it.id) } }
            }
        }
    }
}

@Composable
private fun PopoutNote(text: String) {
    Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One option: icon, name, and the game's tooltip under it (without its "Tailoring :4" line, or one that repeats the name). */
@Composable
private fun PopoutItem(option: MenuOption, iconUrl: (String) -> String, onClick: () -> Unit) {
    val effect = option.tooltip?.split("\n")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() && !SKILL_LINE.matches(it) && !it.equals(option.name, ignoreCase = true) }
        ?.joinToString(" · ")
        ?.takeIf { it.isNotEmpty() }
    DropdownMenuItem(
        enabled = option.enabled && option.children.isEmpty(),
        onClick = onClick,
        leadingIcon = { GameIcon(option.icon, iconUrl, 26.dp) },
        text = {
            Column {
                Text(option.name, maxLines = 2)
                effect?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
    )
}

/** The tooltip's skill line, "Tailoring :4" in the game's language: the panel's header already says it. */
private val SKILL_LINE = Regex("""^[^:]+:\s*\d+$""")
