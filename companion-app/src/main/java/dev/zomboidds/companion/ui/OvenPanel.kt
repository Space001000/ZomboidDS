package dev.zomboidds.companion.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.Container
import dev.zomboidds.companion.domain.Fetched
import dev.zomboidds.companion.domain.Knob
import dev.zomboidds.companion.domain.KnobMark
import dev.zomboidds.companion.domain.OvenControl
import dev.zomboidds.companion.domain.OvenState
import dev.zomboidds.companion.domain.Ovens
import dev.zomboidds.companion.domain.withDegrees
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import dev.zomboidds.companion.domain.stacks
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2

private val DialSize = 148.dp
private val KnobSize = 103.dp // the game's knob (125 px) on its dial (180 px)

/** How long a value the player just set wins over what the game still reports. */
private const val PENDING_MS = 1_500L

/**
 * An oven's or microwave's settings, as the game's window shows them: its two knobs on the game's
 * own dial art (drag one round, or tap a mark on its ring; ‹ › step one mark), Turn on / Turn off,
 * Fahrenheit or Celsius, and on the right what's inside, from the containers around. Reads the oven
 * twice a second; closes when the game says it's gone (more than 3 tiles away).
 */
@Composable
internal fun BoxScope.OvenPanel(
    ovens: Ovens,
    containers: List<Container>?,
    iconUrl: (String) -> String,
    /** Opens the Inventory tab on this container (what's inside). */
    onShowInside: (containerId: String) -> Unit,
    onClose: () -> Unit,
) {
    var oven by remember { mutableStateOf<Fetched<OvenState>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { ovens.closeOven() }
        onClose()
    }
    LaunchedEffect(reload) {
        while (true) {
            val fetched = ovens.ovenState()
            if (fetched is Fetched.Ready && fetched.value.gone) {
                onClose()
                return@LaunchedEffect
            }
            if (fetched is Fetched.Ready || oven !is Fetched.Ready) oven = fetched
            delay(500)
        }
    }
    var message by rememberTransientMessage()
    val act: (OvenControl) -> Unit = { control ->
        scope.launch {
            val result = ovens.ovenControl(control)
            if (result is CommandResult.Failed) message = result.reason
            reload++
        }
    }

    SheetPanel(onDismiss = close) {
            when (val result = oven) {
                null -> LoadingRow("Reading the oven...")
                is Fetched.Failed -> FailedRow(result.reason, "Close", close)
                is Fetched.Ready -> {
                    val o = result.value
                    OvenHeader(o, iconUrl, onToggle = { act(OvenControl.Toggle) }, onClose = close)
                    message?.let { Text(it, color = ErrorText, style = MaterialTheme.typography.bodySmall) }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Knobs(o, iconUrl, act)
                        }
                        val inside = o.container?.let { id -> containers?.firstOrNull { it.id == id } }
                        if (inside != null) {
                            Inside(inside, iconUrl, Modifier.width(196.dp).fillMaxHeight(), onOpen = { onShowInside(inside.id) })
                        }
                    }
                }
            }
    }
}

@Composable
private fun OvenHeader(o: OvenState, iconUrl: (String) -> String, onToggle: () -> Unit, onClose: () -> Unit) {
    Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GameIcon(o.icon, iconUrl, 36.dp)
        Column(Modifier.weight(1f)) {
            Text(o.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val (status, bad) = when {
                o.broken -> "Broken" to true
                !o.powered -> "No power." to true
                o.on -> "On" to false
                else -> "Off" to false
            }
            Text(status, style = MaterialTheme.typography.bodySmall,
                color = if (bad) Danger else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        // The game's button: greyed without electricity (ISOvenUI:updateButtons).
        if (o.on) {
            FilledTonalButton(onClick = onToggle) { Text("Turn off") }
        } else {
            Button(onClick = onToggle, enabled = o.powered && !o.broken) { Text("Turn on") }
        }
        TextButton(onClick = onClose) { Text("Close") }
    }
}

/** A value the player just set, shown until the game reports it (or a moment has passed). */
private class Pending(val value: Int, val at: Long = SystemClock.uptimeMillis()) {
    fun current(reported: Int): Int? = value.takeIf { it != reported && SystemClock.uptimeMillis() - at < PENDING_MS }
}

@Composable
private fun Knobs(o: OvenState, iconUrl: (String) -> String, act: (OvenControl) -> Unit) {
    var tempPending by remember { mutableStateOf<Pending?>(null) }
    var timerPending by remember { mutableStateOf<Pending?>(null) }
    val temperature = tempPending?.current(o.temperature) ?: o.temperature
    // While it runs, the timer knob shows what's left, as the game's does.
    val reportedTimer = o.left ?: o.timer
    val timerShown = timerPending?.current(reportedTimer) ?: reportedTimer

    Row(Modifier.height(30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (o.microwave) "Power" else "Temperature", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Fahrenheit or Celsius: the window's tick box (ovens only).
        o.celsius?.let { celsius ->
            SingleChoiceSegmentedButtonRow(Modifier.height(30.dp)) {
                listOf(false to "°F", true to "°C").forEachIndexed { i, (c, label) ->
                    SegmentedButton(selected = celsius == c, onClick = { if (celsius != c) act(OvenControl.Units(c)) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2), icon = {},
                        contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Text(label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Timer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val tempMark = o.temperatureDial.markFor(temperature)
        KnobColumn(o.temperatureDial, o.dial, tempMark, iconUrl,
            text = { mark -> valueText(o.withDegrees(mark?.label ?: o.temperatureText)) },
            onSet = { mark ->
                tempPending = Pending(mark.value)
                act(OvenControl.Temperature(mark.value))
            })
        val timerMark = o.timerDial.markFor(timerShown)
        KnobColumn(o.timerDial, o.dial, timerMark, iconUrl,
            text = { mark ->
                val minutes = if (mark == timerMark) timerShown else mark?.value ?: 0
                if (minutes == 0) valueText("No timer") else valueText("$minutes min")
            },
            onSet = { mark ->
                timerPending = Pending(mark.value)
                act(OvenControl.Timer(mark.value))
            })
    }
    val set = if (o.left != null) "${o.timer} min set, ${o.left} left." else "${o.timer} min set."
    val info = when {
        o.broken -> "It's broken."
        !o.on -> "Off. Turn on starts it with these settings."
        o.microwave -> if (o.timer > 0) "$set The microwave turns itself off when the timer runs out." else null
        o.timer > 0 -> "$set When it runs out the timer rings; the oven stays on."
        else -> "No timer: it stays on until you turn it off."
    }
    info?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** "400 °F": the number large, the unit small, like the mockup. */
private fun valueText(text: String) = buildAnnotatedString {
    val space = text.indexOf(' ')
    if (space < 0 || text.first().isLetter()) {
        withStyle(SpanStyle(fontSize = 16.sp)) { append(text) }
    } else {
        withStyle(SpanStyle(fontSize = 22.sp)) { append(text.substring(0, space)) }
        withStyle(SpanStyle(fontSize = 13.sp)) { append(text.substring(space)) }
    }
}

/** One knob: the dial, and the value under it between ‹ and ›, which step one mark down or up. */
@Composable
private fun KnobColumn(
    knob: Knob,
    dialArt: String?,
    shown: KnobMark?,
    iconUrl: (String) -> String,
    text: (KnobMark?) -> AnnotatedString,
    onSet: (KnobMark) -> Unit,
) {
    var dragging by remember { mutableStateOf<KnobMark?>(null) }
    val mark = dragging ?: shown
    val steps = knob.byValue
    val index = steps.indexOf(mark)
    Column(Modifier.width(DialSize), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Dial(knob, dialArt, mark, iconUrl, onDrag = { dragging = it }, onSet = { set ->
            dragging = null
            if (set != shown) onSet(set)
        })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundStepButton("‹", enabled = index > 0) { onSet(steps[index - 1]) }
            Text(text(mark), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            RoundStepButton("›", enabled = index in 0 until steps.lastIndex) { onSet(steps[index + 1]) }
        }
    }
}

/**
 * The game's dial: its art with the marks, and its knob turned to [mark]. A drag turns it, stopping
 * on the marks as the game's knob does (ISKnob:onMouseMove); a tap on the ring jumps to the mark
 * there. [onDrag] reports the mark under the finger, [onSet] the one it ends on.
 */
@Composable
private fun Dial(knob: Knob, dialArt: String?, mark: KnobMark?, iconUrl: (String) -> String,
                 onDrag: (KnobMark) -> Unit, onSet: (KnobMark) -> Unit) {
    Box(
        Modifier.size(DialSize)
            .pointerInput(knob) { detectTapGestures { at -> knobMarkAt(knob, degreesAt(at, size))?.let(onSet) } }
            .pointerInput(knob) {
                // The mark under the finger while it drags; where it ends is the setting.
                var last: KnobMark? = null
                fun markAt(at: Offset) = knobMarkAt(knob, degreesAt(at, size))
                detectDragGestures(
                    onDragStart = { at -> markAt(at)?.let { last = it; onDrag(it) } },
                    onDrag = { change, _ -> markAt(change.position)?.let { last = it; onDrag(it) } },
                    onDragEnd = { last?.let(onSet); last = null },
                    onDragCancel = { last?.let(onSet); last = null },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        knob.art?.let { AsyncImage(model = iconUrl(it), contentDescription = null, modifier = Modifier.fillMaxSize()) }
        if (dialArt != null && mark != null) {
            AsyncImage(model = iconUrl(dialArt), contentDescription = null,
                modifier = Modifier.size(KnobSize).graphicsLayer { rotationZ = mark.angle })
        }
    }
}

/** Degrees clockwise from the top of the dial's middle, 0–360. */
private fun degreesAt(at: Offset, size: IntSize): Float {
    val dx = at.x - size.width / 2f
    val dy = at.y - size.height / 2f
    val degrees = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
    return (degrees + 360f) % 360f
}

/**
 * The mark a finger at [degrees] turns the knob to, as the game's knob decides (ISKnob:onMouseMove):
 * halfway between two marks it goes to the next; past halfway from the last mark round to the top,
 * back to the first.
 */
internal fun knobMarkAt(knob: Knob, degrees: Float): KnobMark? {
    val marks = knob.byAngle
    if (marks.isEmpty()) return null
    val last = marks.last().angle
    if (degrees >= last + (360f - last) / 2f) return marks.first()
    var prev = 0f
    for (i in marks.indices) {
        val cur = marks[i].angle
        val next = if (i == marks.lastIndex) 360f else marks[i + 1].angle
        if (degrees >= prev + (cur - prev) / 2f && degrees < cur + (next - cur) / 2f) return marks[i]
        prev = cur
    }
    return marks.first()
}

/** What's in the oven, with the cooking bars the inventory shows; a tap opens it in the Inventory tab. */
@Composable
private fun Inside(container: Container, iconUrl: (String) -> String, modifier: Modifier, onOpen: () -> Unit) {
    val stacks = remember(container.items) { container.items.orEmpty().stacks() }
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Inside · ${stacks.sumOf { it.count }}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(start = 4.dp))
            TextButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Open ›") }
        }
        if (stacks.isEmpty()) {
            Text("Empty. Put food in from the Inventory tab.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            stacks.forEach { stack ->
                ListRow(stack, iconUrl, selected = false, worn = false, onClick = onOpen)
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
