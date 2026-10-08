package dev.zomboidds.companion.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.GameControls
import dev.zomboidds.companion.domain.HereOrder
import dev.zomboidds.companion.domain.HereState
import dev.zomboidds.companion.domain.ItemActions
import dev.zomboidds.companion.domain.MenuOption
import dev.zomboidds.companion.domain.find
import dev.zomboidds.companion.domain.stableKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** How long something must be in front of you before its card goes on top (not while walking past). */
private const val FRONT_SETTLE_MS = 1_000L

/** How long the screen holds still after a finger lets go, so a card never moves under a tap. */
private const val HOLD_AFTER_TOUCH_MS = 1_000L

/** How long the game's reason (why an option is greyed, why it failed) stays up. */
private const val MESSAGE_MS = 4_000L

/** An open list: under the option [topKey], down [names]; [base] names deep is where it opened. */
private data class SheetRef(val topKey: String, val names: List<String>, val base: Int)

/**
 * The game's world menu for where you stand (what the controller's interact button opens). The game
 * keeps it up to date by itself as you move and turn, and after an action ran (a door you opened
 * now says "Close Door"). To keep that calm:
 *  - one card per object, in [HereOrder]: what has been in front of you on top, then the rest in
 *    the order they showed up; cards slide to their new place;
 *  - lists of objects (Disassemble) and loose actions sit in a row at the bottom;
 *  - every list (those, and an action's submenu) opens as a sheet over the cards, never inside one;
 *  - while you touch the screen, and a second after, it shows what it showed.
 */
@Composable
fun HereScreen(
    here: HereState?,
    controls: GameControls,
    actions: ItemActions,
    iconUrl: (String) -> String,
    modifier: Modifier = Modifier,
) {
    // Tell the game we're looking; repeated because it expires (e.g. if the app is closed).
    WatchHere(controls)

    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }

    // Hold still while touched: show the menu as it was, until a second after the finger lifts.
    var touching by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(touching) {
        if (touching) held = true else { delay(HOLD_AFTER_TOUCH_MS); held = false }
    }
    val live = here?.menu
    var frozen by remember { mutableStateOf(live) }
    val shown = if (held) frozen else live
    SideEffect { if (!held) frozen = live }

    // What's in front goes on top once it has stayed in front for a moment (and not mid-touch).
    val order = remember { HereOrder() }
    var orderVersion by remember { mutableIntStateOf(0) }
    val liveFront = live?.options?.firstOrNull { it.front }?.stableKey
    LaunchedEffect(liveFront) {
        if (liveFront == null) return@LaunchedEffect
        delay(FRONT_SETTLE_MS)
        snapshotFlow { held }.first { !it }
        order.promote(liveFront)
        orderVersion++
    }

    var sheet by remember { mutableStateOf<SheetRef?>(null) }

    /** Runs [option], found under [topKey] by [path] in the game's current menu if it changed since. */
    fun run(topKey: String, path: List<String>, option: MenuOption) {
        message = null
        sheet = null
        val current = here?.menu ?: return
        val target = if (current.menuId == shown?.menuId) option else current.find(topKey, path)
        if (target == null || !target.enabled || target.children.isNotEmpty()) {
            message = "${option.name} isn't there anymore"
            return
        }
        held = false // the tap is done: show what it changed (the door now says "Close Door")
        scope.launch {
            val result = actions.selectMenuOption(current.menuId, target.id)
            if (result is CommandResult.Failed) message = result.reason
        }
    }
    // The game's reason fades after a while; it floats over the cards, so they never move for it.
    LaunchedEffect(message) {
        if (message != null) { delay(MESSAGE_MS); message = null }
    }
    val onGreyed: (MenuOption) -> Unit = { message = it.tooltip ?: "${it.name} isn't possible right now" }

    Box(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                touching = true
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
                touching = false
            }
        },
    ) {
        when {
            shown != null -> {
                val arranged = remember(shown, orderVersion) { order.arrange(shown) }
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ObjectList(arranged.objects, iconUrl, Modifier.weight(1f),
                        onRun = ::run, onGreyed = onGreyed,
                        onOpen = { key, names -> sheet = SheetRef(key, names, names.size) })
                    if (arranged.tray.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Tray(arranged.tray, onRun = { run(it.stableKey, emptyList(), it) }, onGreyed = onGreyed,
                            onOpen = { sheet = SheetRef(it.stableKey, emptyList(), 0) })
                    }
                }
                sheet?.let { ref ->
                    val source = shown.find(ref.topKey, ref.names)
                    if (source == null || source.children.isEmpty()) {
                        SideEffect { sheet = null } // gone (you walked away): close it
                    } else {
                        val top = shown.find(ref.topKey, emptyList())!!
                        Sheet(
                            title = (listOf(top.name) + ref.names).joinToString(" · "),
                            icon = top.icon?.takeIf { !top.tray },
                            options = source.children, iconUrl = iconUrl,
                            onRun = { run(ref.topKey, ref.names + it.name, it) },
                            onGreyed = onGreyed,
                            onOpen = { sheet = ref.copy(names = ref.names + it.name) },
                            onBack = if (ref.names.size > ref.base) ({ sheet = ref.copy(names = ref.names.dropLast(1)) }) else null,
                            onClose = { sheet = null },
                        )
                    }
                }
                message?.let {
                    Surface(
                        Modifier.align(Alignment.TopCenter).padding(horizontal = 24.dp),
                        shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.inverseSurface, shadowElevation = 4.dp,
                    ) {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
            here?.unavailable != null -> Text(here.unavailable, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Text("Looking around...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The objects as cards in one column; each slides to its new place when the order changes. */
@Composable
private fun ObjectList(
    objects: List<MenuOption>,
    iconUrl: (String) -> String,
    modifier: Modifier,
    onRun: (topKey: String, path: List<String>, MenuOption) -> Unit,
    onGreyed: (MenuOption) -> Unit,
    onOpen: (topKey: String, names: List<String>) -> Unit,
) {
    val listState = rememberLazyListState()
    // A card going on top while you're at the top: stay at the top. (The list otherwise keeps the
    // card that was first in view, and the new top card would sit just out of sight above it.)
    val topKey = objects.firstOrNull()?.stableKey
    var lastTop by remember { mutableStateOf(topKey) }
    SideEffect {
        if (topKey != lastTop) {
            if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) listState.requestScrollToItem(0)
            lastTop = topKey
        }
    }
    LazyColumn(modifier, state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(objects, key = { it.stableKey }) { obj ->
            ObjectCard(obj, iconUrl, Modifier.animateItem(),
                onRun = { onRun(obj.stableKey, listOf(it.name), it) },
                onGreyed = onGreyed,
                onOpen = { onOpen(obj.stableKey, listOf(it.name)) })
        }
    }
}

/** One object around you: its icon and name, its actions as chips; outlined while it's in front. */
@Composable
private fun ObjectCard(
    obj: MenuOption,
    iconUrl: (String) -> String,
    modifier: Modifier = Modifier,
    onRun: (MenuOption) -> Unit,
    onGreyed: (MenuOption) -> Unit,
    onOpen: (MenuOption) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = colors.surfaceContainer,
        border = BorderStroke(1.5.dp, if (obj.front) colors.primary else Color.Transparent),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ObjectIcon(obj.icon, iconUrl)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(obj.name, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                    if (obj.front) {
                        Text("In front", style = MaterialTheme.typography.labelSmall, color = colors.onPrimary,
                            modifier = Modifier.background(colors.primary, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp))
                    }
                }
                Chips(obj.children, onRun = onRun, onGreyed = onGreyed, onOpen = onOpen)
            }
        }
    }
}

/** The game's icon for an object; without one, just the space, so everything lines up. */
@Composable
private fun ObjectIcon(icon: String?, iconUrl: (String) -> String) {
    Box(
        Modifier.size(32.dp).let {
            if (icon != null) it.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp)) else it
        },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            AsyncImage(model = iconUrl(icon), contentDescription = null, filterQuality = FilterQuality.None, modifier = Modifier.size(26.dp))
        }
    }
}

/**
 * The bottom row: lists of objects (Disassemble), with how many you can do now, and loose actions
 * (Sit on ground). Scrolls sideways when it doesn't fit.
 */
@Composable
private fun Tray(options: List<MenuOption>, onRun: (MenuOption) -> Unit, onGreyed: (MenuOption) -> Unit, onOpen: (MenuOption) -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                if (option.children.isNotEmpty()) {
                    val possible = option.children.count { it.enabled }
                    AssistChip(
                        onClick = { onOpen(option) },
                        label = {
                            val muted = MaterialTheme.colorScheme.onSurfaceVariant
                            Text(buildAnnotatedString {
                                append(option.name + " ")
                                withStyle(SpanStyle(color = muted, fontSize = MaterialTheme.typography.labelSmall.fontSize)) {
                                    append("$possible/${option.children.size}")
                                }
                                append(" ›")
                            })
                        },
                        colors = if (possible == 0) greyedChipColors() else AssistChipDefaults.assistChipColors(),
                    )
                } else {
                    Chip(option, onRun = onRun, onGreyed = onGreyed, onOpen = {})
                }
            }
        }
    }
}

/** A list over the cards: a list of objects or an action's submenu. Never pushes the cards around. */
@Composable
private fun Sheet(
    title: String,
    icon: String?,
    options: List<MenuOption>,
    iconUrl: (String) -> String,
    onRun: (MenuOption) -> Unit,
    onGreyed: (MenuOption) -> Unit,
    onOpen: (MenuOption) -> Unit,
    onBack: (() -> Unit)?,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose))
        BoxWithConstraints(Modifier.align(Alignment.BottomCenter)) {
            Surface(
                Modifier.fillMaxWidth().heightIn(max = maxHeight * 0.78f),
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest, shadowElevation = 8.dp,
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (icon != null) ObjectIcon(icon, iconUrl)
                        Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        onBack?.let { SheetButton("‹", it) }
                        SheetButton("✕", onClose)
                    }
                    Box(Modifier.verticalScroll(rememberScrollState())) {
                        Chips(options, onRun = onRun, onGreyed = onGreyed, onOpen = onOpen)
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** Actions as chips: tap runs it; greyed ones show the game's reason; a submenu (›) opens a sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(options: List<MenuOption>, onRun: (MenuOption) -> Unit, onGreyed: (MenuOption) -> Unit, onOpen: (MenuOption) -> Unit) {
    // Chips at their visual height (32 dp) instead of the default 48 dp touch padding: wrapped rows
    // otherwise get big gaps, and the bottom screen has little room.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { Chip(it, onRun, onGreyed, onOpen) }
        }
    }
}

@Composable
private fun Chip(option: MenuOption, onRun: (MenuOption) -> Unit, onGreyed: (MenuOption) -> Unit, onOpen: (MenuOption) -> Unit) {
    when {
        !option.enabled -> AssistChip(onClick = { onGreyed(option) }, label = { Text(option.name) }, colors = greyedChipColors())
        option.children.isNotEmpty() -> AssistChip(onClick = { onOpen(option) }, label = { Text(option.name + " ›") })
        else -> AssistChip(onClick = { onRun(option) }, label = { Text(option.name) })
    }
}

@Composable
private fun greyedChipColors() =
    AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))

/**
 * While on screen, the game keeps "Here" up to date (the world menu, or the vehicle's menu in a
 * car). Repeated because it expires (e.g. if the app is closed).
 */
@Composable
internal fun WatchHere(controls: GameControls) {
    LaunchedEffect(Unit) {
        while (true) {
            controls.watchHere(true)
            delay(4_000)
        }
    }
    DisposableEffect(Unit) { onDispose { controls.watchHere(false) } }
}
