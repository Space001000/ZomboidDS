package dev.zomboidds.companion.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.zomboidds.companion.domain.AlarmClock
import dev.zomboidds.companion.domain.HotbarSlot

/** A panel from the bottom of the Deck over a dimmed background; tapping outside closes it. */
@Composable
internal fun BoxScope.DeckSheet(onClose: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    )
    Surface(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            // Taps on the panel itself don't reach the background (which would close it).
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
    ) {
        Box(Modifier.padding(14.dp)) { content() }
    }
}

private val SlotHeight = 104.dp
private val SlotMinWidth = 110.dp

/**
 * The game's hotbar, slot by slot: tap one to draw its item (what you held goes back to its slot);
 * the one in hand is outlined. "Put away" puts it back without drawing anything.
 */
@Composable
internal fun WeaponsPanel(hotbar: List<HotbarSlot>, iconUrl: (String) -> String, onDraw: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your hotbar", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            val inHand = hotbar.firstOrNull { it.inHand && it.item != null }
            if (inHand != null) {
                // Drawing the slot in hand puts it away, as the game's hotbar key does.
                OutlinedButton(onClick = { onDraw(inHand.slot) }) { Text("Put away") }
            }
        }
        BoxWithConstraints {
            val columns = ((maxWidth + 8.dp) / (SlotMinWidth + 8.dp)).toInt().coerceIn(2, 6)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                hotbar.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { slot -> SlotTile(slot, iconUrl, onDraw, Modifier.weight(1f)) }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotTile(slot: HotbarSlot, iconUrl: (String) -> String, onDraw: (Int) -> Unit, modifier: Modifier) {
    val item = slot.item
    Surface(
        onClick = { onDraw(slot.slot) },
        enabled = item != null,
        shape = RoundedCornerShape(12.dp),
        color = if (slot.inHand) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (slot.inHand) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.height(SlotHeight),
    ) {
        Column(
            Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        ) {
            if (item != null) {
                item.icon?.let {
                    AsyncImage(model = iconUrl(it), contentDescription = null, filterQuality = FilterQuality.None, modifier = Modifier.size(36.dp))
                }
                Text(item.name, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                if (slot.inHand) "${slot.name} · in hand" else if (item == null) "${slot.name} · empty" else slot.name,
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (item == null) 0.5f else 1f),
            )
        }
    }
}

/**
 * The alarm of the player's watch: hours and minutes with big arrows (minutes in steps of 10, as
 * the game's own alarm dialog), a few shortcuts, on/off. [now] is the game's clock ("14:25" or
 * "2:25 PM") for "In 1 hour"; without it that shortcut is left out.
 */
@Composable
internal fun AlarmPanel(clock: AlarmClock, now: String?, onSet: (hour: Int, minute: Int, on: Boolean) -> Unit) {
    var hour by remember(clock) { mutableIntStateOf(clock.hour) }
    var minute by remember(clock) { mutableIntStateOf(clock.minute / 10 * 10) }
    var on by remember(clock) { mutableStateOf(clock.on) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Alarm · ${clock.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Button(onClick = { onSet(hour, minute, on) }) { Text("Set") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Stepper("%02d".format(hour), onUp = { hour = (hour + 1) % 24 }, onDown = { hour = (hour + 23) % 24 })
            Text(":", fontSize = 48.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp))
            Stepper("%02d".format(minute), onUp = { minute = (minute + 10) % 60 }, onDown = { minute = (minute + 50) % 60 })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(6, 7, 8).forEach { h ->
                    AssistChip(onClick = { hour = h; minute = 0; on = true }, label = { Text("$h:00") })
                }
                gameHour(now)?.let { current ->
                    AssistChip(onClick = { hour = (current + 1) % 24; minute = 0; on = true }, label = { Text("In 1 hour") })
                }
            }
            Text("Alarm on", Modifier.padding(end = 8.dp))
            Switch(checked = on, onCheckedChange = { on = it })
        }
    }
}

@Composable
private fun Stepper(value: String, onUp: () -> Unit, onDown: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StepButton("▲", onUp)
        Text(value, fontSize = 52.sp, fontWeight = FontWeight.Bold)
        StepButton("▼", onDown)
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.width(88.dp).height(44.dp)) {
        Box(contentAlignment = Alignment.Center) { Text(label, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp) }
    }
}

/** The hour of the game's clock text ("14:25", "2:25 PM"), 0-23; null if it isn't one of those. */
internal fun gameHour(time: String?): Int? {
    val match = time?.trim()?.let { Regex("""^(\d{1,2}):(\d{2})\s*(AM|PM)?$""", RegexOption.IGNORE_CASE).matchEntire(it) } ?: return null
    val hour = match.groupValues[1].toInt()
    return when (match.groupValues[3].uppercase()) {
        "AM" -> hour % 12
        "PM" -> hour % 12 + 12
        else -> hour.takeIf { it in 0..23 }
    }
}
