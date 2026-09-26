package dev.zomboidds.companion.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.DeckClock
import dev.zomboidds.companion.domain.DeckCommand
import dev.zomboidds.companion.domain.DeckState
import dev.zomboidds.companion.domain.GameControls
import dev.zomboidds.companion.domain.GameSpeed
import dev.zomboidds.companion.domain.TimeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Command deck: the game's speed buttons, its clock (with a watch), and the commands the
 * player put on it ([chosen], in order), as big buttons. [onEdit] opens the list to change them.
 */
@Composable
fun CommandDeckScreen(
    time: TimeState?,
    deck: DeckState?,
    chosen: List<String>,
    controls: GameControls,
    iconUrl: (String) -> String,
    onEdit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var failure by remember { mutableStateOf<String?>(null) }
    fun run(action: suspend () -> CommandResult) {
        failure = null
        scope.launch {
            val result = action()
            if (result is CommandResult.Failed) failure = result.reason
        }
    }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            time == null -> Text("Waiting for the game...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            !time.canChange -> Text("The game speed can't be changed in multiplayer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> SpeedButtons(time.speed, iconUrl, enabled = !time.gameMenuOpen) { speed -> run { controls.setSpeed(speed) } }
        }
        if (time?.gameMenuOpen == true) {
            Text("The game's menu is open: close it to change the speed.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        deck?.clock?.let { ClockLine(it) }
        failure?.let { Text(it, color = ErrorText, style = MaterialTheme.typography.bodySmall) }
        if (deck != null) {
            // The player's buttons, as far as this game offers them (an older mod may lack some).
            val commands = chosen.mapNotNull { id -> deck.commands.firstOrNull { it.id == id } }
            CommandGrid(commands, iconUrl, onRun = { command -> run { controls.runDeckCommand(command.id) } }, onEdit = onEdit)
        }
    }
}

/** What the game's clock shows (only with a watch): date and time, and the alarm if it's set. */
@Composable
private fun ClockLine(clock: DeckClock) {
    Row(Modifier.fillMaxWidth()) {
        Text(listOfNotNull(clock.date, clock.time).joinToString(" · "), Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        clock.alarm?.let {
            Text("Alarm $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Big enough for a thumb; four across on the Thor, fewer on a narrow phone. */
private val DeckTileHeight = 92.dp
private val DeckTileMinWidth = 110.dp

@Composable
private fun CommandGrid(commands: List<DeckCommand>, iconUrl: (String) -> String, onRun: (DeckCommand) -> Unit, onEdit: () -> Unit) {
    BoxWithConstraints {
        val columns = ((maxWidth + 8.dp) / (DeckTileMinWidth + 8.dp)).toInt().coerceIn(2, 6)
        val tiles: List<DeckCommand?> = commands + null // null: the "Add or edit" tile
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { command ->
                        val modifier = Modifier.weight(1f).height(DeckTileHeight)
                        if (command == null) EditTile(onEdit, modifier) else CommandTile(command, iconUrl, onRun, modifier)
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * One command. Modes (search mode, flashlight) get a green dot while on. Dropping your bag is one
 * tap you can't take back, so it asks first: the first tap arms it, a second one within 3 s drops.
 */
@Composable
private fun CommandTile(command: DeckCommand, iconUrl: (String) -> String, onRun: (DeckCommand) -> Unit, modifier: Modifier) {
    val asksFirst = command.id in ASKS_FIRST
    var armed by remember(command.id) { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3_000)
            armed = false
        }
    }
    Surface(
        onClick = {
            when {
                asksFirst && !armed -> armed = true
                else -> { armed = false; onRun(command) }
            }
        },
        enabled = command.available,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (armed) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null,
        modifier = modifier,
    ) {
        Box {
            Column(
                Modifier.fillMaxSize().padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
                command.icon?.let {
                    AsyncImage(model = iconUrl(it), contentDescription = null, filterQuality = FilterQuality.None,
                        alpha = if (command.available) 1f else 0.35f, modifier = Modifier.size(34.dp))
                }
                Text(
                    if (armed) "Tap again to ${label(command).lowercase()}" else label(command),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = when {
                        armed -> MaterialTheme.colorScheme.error
                        command.available -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    },
                )
            }
            if (command.on == true) {
                Surface(shape = CircleShape, color = Good,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(8.dp)) {}
            }
        }
    }
}

@Composable
private fun EditTile(onEdit: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onEdit,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("+", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text("Add or edit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Commands whose one tap can't be taken back: the tile asks first. */
private val ASKS_FIRST = setOf("drop_bag")

/**
 * Short labels for the commands we know (the game's own names are its key binding texts, e.g.
 * "Equip/Turn On/Off Light Source"); a command from a newer mod keeps the game's name.
 */
internal fun label(command: DeckCommand) = when (command.id) {
    "zoom_in" -> "Zoom in"
    "zoom_out" -> "Zoom out"
    "search_mode" -> "Search mode"
    "flashlight" -> "Flashlight"
    "map" -> "Map"
    "sit" -> "Sit"
    "drop_bag" -> "Drop bag"
    "shout" -> "Shout"
    else -> command.name
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
