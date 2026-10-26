package dev.zomboidds.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.zomboidds.companion.AppSettings
import dev.zomboidds.companion.domain.DeckCommand

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
        Text("Hold a command on the Deck to pin it to the tab bar.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        // The editor is as wide as the Deck, so it knows where the Deck's rows break.
        BoxWithConstraints {
        val columns = deckColumns(maxWidth)
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(EditorRowGap)) {
            // The Deck's own commands, draggable by the handle, with the Deck's rows numbered beside them.
            Row {
                RowMarks(onDeck.size, columns)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EditorRowGap)) {
                    DragRows(onDeck, key = { it.id }, rowHeight = EditorRowHeight, gap = EditorRowGap, onMove = { from, to ->
                        val ids = onDeck.map { it.id }.toMutableList()
                        ids.add(to, ids.removeAt(from))
                        onChange(ids + unknown)
                    }) { command, handle, lifted ->
                        CommandRow(command, iconUrl, on = true, onSwitch = { onChange(chosen - command.id) }, handle = handle, lifted = lifted)
                    }
                }
            }
            rest.forEach { command ->
                CommandRow(command, iconUrl, on = false, onSwitch = { onChange(chosen + command.id) }, handle = Modifier)
            }
        }
        }
    }
}

/**
 * Beside the Deck's commands: the Deck row each one lands in ([columns] to a row, "Add or edit"
 * after the last), as a number at a row's first command and a line from where the previous row
 * ends. Fixed to the list's slots, so a dragged command passes them.
 */
@Composable
private fun RowMarks(count: Int, columns: Int) {
    if (count == 0) return
    val step = EditorRowHeight + EditorRowGap
    val line = MaterialTheme.colorScheme.outline
    Box(
        Modifier.width(RowMarkWidth).height(step * count - EditorRowGap)
            .drawBehind {
                // The bracket of each row: from its first command to its last.
                for (start in 0 until count step columns) {
                    val end = minOf(start + columns, count)
                    val x = size.width - 6.dp.toPx()
                    drawLine(line, Offset(x, (step * start).toPx() + 6.dp.toPx()), Offset(x, (step * end - EditorRowGap).toPx() - 6.dp.toPx()),
                        strokeWidth = 2.dp.toPx())
                }
            },
    ) {
        for (start in 0 until count step columns) {
            Text(
                "${start / columns + 1}",
                Modifier.offset(y = step * start).height(EditorRowHeight).width(RowMarkWidth - 10.dp).wrapContentHeight(),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
    }
}

private val RowMarkWidth = 30.dp

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
    "drop_held" -> "Asks first; then picks them up again"
    "auto_walk" -> "Keeps walking the way you were going"
    "emotes" -> "The emote wheel's emotes, and your favourites"
    "sit" -> "Tap again to stand up"
    "weapons" -> "Your hotbar: draw or put away"
    "alarm" -> "Your watch's alarm"
    else -> null
}
