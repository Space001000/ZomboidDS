package dev.zomboidds.companion.ui

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import dev.zomboidds.companion.domain.MenuOption
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import dev.zomboidds.companion.domain.HereState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import dev.zomboidds.companion.domain.ItemActions
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import coil3.compose.AsyncImage
import androidx.compose.ui.unit.dp
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.GameControls
import dev.zomboidds.companion.domain.GameSpeed
import dev.zomboidds.companion.domain.TimeState
import kotlinx.coroutines.launch

/** Things you want one tap away while playing with the controller. Grows in Phase 7. */
@Composable
fun CommandDeckScreen(
    time: TimeState?,
    here: HereState?,
    controls: GameControls,
    actions: ItemActions,
    iconUrl: (String) -> String,
) {
    val scope = rememberCoroutineScope()
    var failure by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            time == null -> Text("Waiting for the game...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            !time.canChange -> Text("The game speed can't be changed in multiplayer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> SpeedButtons(time.speed, iconUrl, enabled = !time.gameMenuOpen) { speed ->
                failure = null
                scope.launch {
                    val result = controls.setSpeed(speed)
                    if (result is CommandResult.Failed) failure = result.reason
                }
            }
        }
        if (time?.gameMenuOpen == true) {
            Text("The game's menu is open: close it to change the speed.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        failure?.let { Text(it, color = ErrorText, style = MaterialTheme.typography.bodySmall) }
        HorizontalDivider()
        Here(here, controls, actions, iconUrl, Modifier.weight(1f))
    }
}

/**
 * The game's world menu for where you stand (what the controller's interact button opens), as
 * cards: each object with its actions as one-tap chips, loose actions as chips at the end.
 * While this is on screen the game keeps it up to date by itself as you move and turn, and after
 * an action ran (a door you opened now says "Close Door").
 */
@Composable
private fun Here(here: HereState?, controls: GameControls, actions: ItemActions, iconUrl: (String) -> String, modifier: Modifier) {
    // Tell the game we're looking; repeated because it expires (e.g. if the app is closed).
    LaunchedEffect(Unit) {
        while (true) {
            controls.watchHere(true)
            delay(4_000)
        }
    }
    DisposableEffect(Unit) { onDispose { controls.watchHere(false) } }

    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    fun run(menuId: String, option: MenuOption) {
        message = null
        scope.launch {
            val result = actions.selectMenuOption(menuId, option.id)
            if (result is CommandResult.Failed) message = result.reason
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        val menu = here?.menu
        when {
            menu != null -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val (objects, loose) = menu.options.partition { it.children.isNotEmpty() }
                objects.forEach { obj ->
                    ObjectCard(obj, iconUrl, onRun = { run(menu.menuId, it) }, onGreyed = { message = it })
                }
                if (loose.isNotEmpty()) {
                    // Actions that don't belong to an object (Sit on ground, a light switch): their own
                    // group with a heading, so they don't read as part of the card above.
                    ObjectCard(
                        MenuOption(id = "loose", name = "Other", enabled = true, children = loose),
                        iconUrl, onRun = { run(menu.menuId, it) }, onGreyed = { message = it },
                    )
                }
            }
            here?.unavailable != null -> Text(here.unavailable, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Text("Looking around...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The icon (32 dp) plus the gap after it: where chips start. */
private val IconColumn = 42.dp

/** One object in front of you: its icon and name, its actions as chips below. */
@Composable
private fun ObjectCard(obj: MenuOption, iconUrl: (String) -> String, onRun: (MenuOption) -> Unit, onGreyed: (String) -> Unit) {
    // A deeper submenu (e.g. which item to use) opens in place under the card.
    var open by remember(obj.id) { mutableStateOf<MenuOption?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // The game's icon for the object; without one, just the space, so everything lines up.
        val icon = obj.icon
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
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(obj.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Chips(obj.children, onRun = onRun, onGreyed = onGreyed, onOpen = { open = if (open == it) null else it }, openId = open?.id)
            open?.let { sub ->
                Chips(sub.children, onRun = onRun, onGreyed = onGreyed)
            }
        }
    }
}

/** Actions as chips: tap runs it; greyed ones show the game's reason; submenus open under. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(
    options: List<MenuOption>,
    onRun: (MenuOption) -> Unit,
    onGreyed: (String) -> Unit,
    onOpen: (MenuOption) -> Unit = {},
    openId: String? = null,
) {
    // Chips at their visual height (32 dp) instead of the default 48 dp touch padding: wrapped rows
    // otherwise get big gaps, and the bottom screen has little room.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            val submenu = option.children.isNotEmpty()
            val label = if (submenu) option.name + " ›" else option.name
            when {
                !option.enabled -> AssistChip(
                    onClick = { onGreyed(option.tooltip ?: "${option.name} isn't possible right now") },
                    label = { Text(option.name) },
                    colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)),
                )
                submenu -> FilterChip(selected = option.id == openId, onClick = { onOpen(option) }, label = { Text(label) })
                else -> AssistChip(onClick = { onRun(option) }, label = { Text(option.name) })
            }
        }
    }
    }
}

/**
 * The game's own speed buttons, as it draws them top right: its icons, red ("On") for the current
 * speed and white ("Off") for the others. One compact row, so the rest of the deck keeps the room.
 */
@Composable
private fun SpeedButtons(current: GameSpeed?, iconUrl: (String) -> String, enabled: Boolean, onSelect: (GameSpeed) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        GameSpeed.entries.forEach { speed ->
            val selected = speed == current
            Surface(
                onClick = { onSelect(speed) },
                enabled = enabled,
                shape = RoundedCornerShape(10.dp),
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = iconUrl(iconName(speed, selected)),
                        contentDescription = label(speed),
                        filterQuality = FilterQuality.None, // pixel art: keep it crisp when scaled up
                        alpha = if (enabled) 1f else 0.35f,
                        modifier = Modifier.height(33.dp), // the game's icons are 22 px tall: 1.5 dp per pixel
                    )
                }
            }
        }
    }
}

/** Texture names in the game's media/ui/speedControls. */
private fun iconName(speed: GameSpeed, on: Boolean): String {
    val base = when (speed) {
        GameSpeed.PAUSED -> "Pause"
        GameSpeed.PLAY -> "Play"
        GameSpeed.FAST -> "FFwd1"
        GameSpeed.FASTER -> "FFwd2"
        GameSpeed.WAIT -> "Wait"
    }
    return base + if (on) "_On" else "_Off"
}

private fun label(speed: GameSpeed) = when (speed) {
    GameSpeed.PAUSED -> "Pause"
    GameSpeed.PLAY -> "Play"
    GameSpeed.FAST -> "Fast"
    GameSpeed.FASTER -> "Faster"
    GameSpeed.WAIT -> "Wait"
}
