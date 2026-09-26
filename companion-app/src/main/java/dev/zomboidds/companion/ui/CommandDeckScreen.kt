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

/** The Command deck: the game's speed buttons, and (next) the commands the player picks. */
@Composable
fun CommandDeckScreen(
    time: TimeState?,
    controls: GameControls,
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
