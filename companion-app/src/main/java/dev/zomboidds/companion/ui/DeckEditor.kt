package dev.zomboidds.companion.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import dev.zomboidds.companion.AppSettings
import dev.zomboidds.companion.domain.DeckCommand
import kotlin.math.roundToInt

private val EditorRowHeight = 52.dp
private val EditorRowGap = 6.dp

/**
 * Choosing the Command deck's buttons: every command the game offers, with a switch. The ones on
 * the Deck come first, in their order; drag one by its handle to move it. Changes apply at once.
 */
@Composable
fun DeckEditor(
    offered: List<DeckCommand>,
    chosen: List<String>,
    iconUrl: (String) -> String,
    onChange: (List<String>) -> Unit,
    onDone: () -> Unit,
) {
    val onDeck = chosen.mapNotNull { id -> offered.firstOrNull { it.id == id } }
    val rest = offered.filter { it.id !in chosen }
    // Ids the game doesn't offer (an older mod) stay in the choice, so they come back with a newer one.
    val unknown = chosen.filter { id -> offered.none { it.id == id } }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your commands", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { onChange(AppSettings.DEFAULT_DECK_COMMANDS) }) { Text("Reset") }
            Button(onClick = onDone, modifier = Modifier.padding(start = 8.dp)) { Text("Done") }
        }
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(EditorRowGap)) {
            ReorderableRows(onDeck, iconUrl, onMove = { from, to ->
                val ids = onDeck.map { it.id }.toMutableList()
                ids.add(to, ids.removeAt(from))
                onChange(ids + unknown)
            }, onSwitch = { command -> onChange(chosen - command.id) })
            rest.forEach { command ->
                CommandRow(command, iconUrl, on = false, onSwitch = { onChange(chosen + command.id) }, handle = Modifier)
            }
        }
    }
}

/** The Deck's own commands, draggable by the handle (long press, then move up or down). */
@Composable
private fun ReorderableRows(
    rows: List<DeckCommand>,
    iconUrl: (String) -> String,
    onMove: (from: Int, to: Int) -> Unit,
    onSwitch: (DeckCommand) -> Unit,
) {
    val step = with(LocalDensity.current) { (EditorRowHeight + EditorRowGap).toPx() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    rows.forEachIndexed { index, command ->
        val isDragged = dragging == command.id
        val handle = Modifier.pointerInput(command.id, rows.size) {
            detectDragGesturesAfterLongPress(
                onDragStart = { dragging = command.id; offset = 0f },
                onDragEnd = {
                    val target = (index + (offset / step).roundToInt()).coerceIn(0, rows.lastIndex)
                    if (target != index) onMove(index, target)
                    dragging = null; offset = 0f
                },
                onDragCancel = { dragging = null; offset = 0f },
                onDrag = { change, amount -> change.consume(); offset += amount.y },
            )
        }
        Box(
            Modifier
                .zIndex(if (isDragged) 1f else 0f)
                .offset { IntOffset(0, if (isDragged) offset.roundToInt() else 0) },
        ) {
            CommandRow(command, iconUrl, on = true, onSwitch = { onSwitch(command) }, handle = handle, lifted = isDragged)
        }
    }
}

@Composable
private fun CommandRow(
    command: DeckCommand,
    iconUrl: (String) -> String,
    on: Boolean,
    onSwitch: () -> Unit,
    handle: Modifier,
    lifted: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (lifted) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = if (lifted) 6.dp else 0.dp,
        modifier = Modifier.fillMaxWidth().height(EditorRowHeight),
    ) {
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            // Only the Deck's own commands can be moved; the handle keeps its space either way.
            Box(handle.width(28.dp).height(EditorRowHeight), contentAlignment = Alignment.Center) {
                if (on) Text("⋮⋮", color = MaterialTheme.colorScheme.outline)
            }
            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                command.icon?.let {
                    AsyncImage(model = iconUrl(it), contentDescription = null, filterQuality = FilterQuality.None, modifier = Modifier.size(28.dp))
                }
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(label(command), style = MaterialTheme.typography.bodyLarge)
                hint(command)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = on, onCheckedChange = { onSwitch() })
        }
    }
}

private fun hint(command: DeckCommand) = when (command.id) {
    "flashlight" -> "The light in your hands or on your belt"
    "search_mode" -> "Stays on until you tap it again"
    "drop_bag" -> "Asks first"
    "sit" -> "Tap again to stand up"
    "weapons" -> "Your hotbar: draw or put away"
    "alarm" -> "Your watch's alarm"
    else -> null
}
