package dev.zomboidds.companion.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.zomboidds.companion.AppSettings
import dev.zomboidds.companion.domain.DeckCommand
import dev.zomboidds.companion.domain.DeckState
import kotlinx.coroutines.delay

/**
 * The pinned Deck commands at the end of the tab bar, after a line so they read as buttons, not
 * tabs. Weapons shows what's in your hands (faded with empty hands); a mode that's on gets the
 * Deck's green dot. [open] is the command whose panel is showing. Nothing at all without pins.
 */
@Composable
internal fun TabPins(
    pins: List<DeckCommand>,
    deck: DeckState,
    open: String?,
    iconUrl: (String) -> String,
    onTap: (DeckCommand) -> Unit,
    onHold: (DeckCommand) -> Unit,
) {
    if (pins.isEmpty()) return
    Box(Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    pins.forEach { command ->
        // The main hand's weapon, else the off hand's.
        val inHand = if (command.id == "weapons") deck.hands.primary?.icon ?: deck.hands.secondary?.icon else null
        val icon = inHand ?: command.icon
        val faded = !command.available || (command.id == "weapons" && inHand == null)
        val isOpen = command.id == open
        Box(
            Modifier.width(52.dp).fillMaxHeight()
                .combinedClickable(role = Role.Button, onLongClick = { onHold(command) }, onClick = { onTap(command) }),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isOpen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = if (isOpen) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.size(42.dp, 38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    icon?.let {
                        AsyncImage(iconUrl(it), label(command), filterQuality = FilterQuality.None,
                            alpha = if (faded) 0.35f else 1f, modifier = Modifier.size(30.dp))
                    }
                    if (command.on == true) {
                        Surface(shape = CircleShape, color = Good,
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(7.dp)) {}
                    }
                }
            }
        }
    }
}

/**
 * Pinning a Deck command to the tab bar, or unpinning it: a card centred over the dim, opened by
 * holding the command on the Deck or its pin. When the bar is full, it asks which pin to replace.
 */
@Composable
internal fun BoxScope.PinCard(
    command: DeckCommand,
    pinned: List<DeckCommand>,
    iconUrl: (String) -> String,
    onChange: (List<String>) -> Unit,
    onClose: () -> Unit,
) {
    val ids = pinned.map { it.id }
    val isPinned = command.id in ids
    val full = !isPinned && pinned.size >= AppSettings.MAX_PINS
    DimmedCard(Modifier.align(Alignment.Center), maxWidth = 340.dp, onDismiss = onClose) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                command.icon?.let {
                    AsyncImage(iconUrl(it), null, filterQuality = FilterQuality.None, modifier = Modifier.padding(end = 10.dp).size(32.dp))
                }
                Text(label(command), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                when {
                    isPinned -> "Pinned to the tab bar."
                    full -> "The tab bar holds ${AppSettings.MAX_PINS}. Pin it in place of:"
                    else -> "Also show it at the end of the tab bar, on every tab."
                },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (full) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pinned.forEach { old ->
                        OutlinedButton(onClick = { onChange(ids.map { if (it == old.id) command.id else it }); onClose() },
                            modifier = Modifier.weight(1f)) {
                            Text(label(old), maxLines = 1)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onClose) { Text("Cancel") }
                when {
                    isPinned -> Button(onClick = { onChange(ids - command.id); onClose() }) { Text("Unpin") }
                    !full -> Button(onClick = { onChange(ids + command.id); onClose() }) { Text("Pin to tab bar") }
                }
            }
        }
    }
}

/** Why a pinned command didn't run, at the bottom of whatever tab is open, for a few seconds. */
@Composable
internal fun BoxScope.PinFailure(reason: String?, onGone: () -> Unit) {
    if (reason == null) return
    LaunchedEffect(reason) {
        delay(3_000)
        onGone()
    }
    Surface(
        Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 6.dp,
    ) {
        Text(reason, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = ErrorText,
            style = MaterialTheme.typography.bodyMedium)
    }
}
