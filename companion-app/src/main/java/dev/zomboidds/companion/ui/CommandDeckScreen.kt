package dev.zomboidds.companion.ui

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
        Text("Game speed", style = MaterialTheme.typography.titleSmall)
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
        failure?.let { Text(it, color = Color(0xFFE57373), style = MaterialTheme.typography.bodySmall) }
        HorizontalDivider()
        Here(here, controls, actions, Modifier.weight(1f))
    }
}

/**
 * The game's world menu for where you stand (what the controller's interact button opens). While
 * this is on screen the game keeps it up to date by itself as you move and turn, and after an
 * option ran (a door you opened now says "Close door").
 */
@Composable
private fun Here(here: HereState?, controls: GameControls, actions: ItemActions, modifier: Modifier) {
    // Tell the game we're looking; repeated because it expires (e.g. if the app is closed).
    LaunchedEffect(Unit) {
        while (true) {
            controls.watchHere(true)
            delay(4_000)
        }
    }
    DisposableEffect(Unit) { onDispose { controls.watchHere(false) } }

    val scope = rememberCoroutineScope()
    var failure by remember { mutableStateOf<String?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Here", style = MaterialTheme.typography.titleSmall)
        failure?.let { Text(it, color = Color(0xFFE57373), style = MaterialTheme.typography.bodySmall) }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            val menu = here?.menu
            when {
                menu != null -> GameMenu(menu, onSelect = { optionId ->
                    scope.launch {
                        val run = actions.selectMenuOption(menu.menuId, optionId)
                        failure = (run as? CommandResult.Failed)?.reason
                    }
                })
                here?.unavailable != null -> Text(here.unavailable, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Text("Looking around...", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
