package dev.zomboidds.companion.ui

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.DeviceControl
import dev.zomboidds.companion.domain.DeviceState
import dev.zomboidds.companion.domain.DeviceWhere
import dev.zomboidds.companion.domain.Devices
import dev.zomboidds.companion.domain.DrawerFit
import dev.zomboidds.companion.domain.DrawerGroup
import dev.zomboidds.companion.domain.DrawerItem
import dev.zomboidds.companion.domain.Fetched
import dev.zomboidds.companion.domain.Frequency
import dev.zomboidds.companion.domain.MediaKind
import dev.zomboidds.companion.domain.MediaSlot
import dev.zomboidds.companion.domain.Preset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// The radio's display: the game's own window draws its frequency on a green LCD.
private val LcdBack = Color(0xFF0F140D)
private val LcdEdge = Color(0xFF2B3324)
private val LcdText = Color(0xFFB8E07A)
private val LcdDim = Color(0xFF7F9B57)

private val SlotSize = 34.dp
private const val VOLUME_STEPS = 10

/** Where a dragged drawer item can be dropped: each slot's place on screen. */
private class DropTargets {
    val rects = mutableStateMapOf<DrawerFit, Rect>()
}

/**
 * Device Options: a TV, radio, walkie-talkie, CD player or car radio as the game's window shows it,
 * with only the parts it has. Things that fit (tapes, CDs, batteries, headphones) are in the drawer
 * on the right: tap one to put it in, or hold and drag it onto its slot. Reads the device twice a
 * second; closes when the game says it's gone (the player walked away or put it away).
 */
@Composable
internal fun BoxScope.DevicePanel(devices: Devices, iconUrl: (String) -> String, onClose: () -> Unit) {
    var device by remember { mutableStateOf<Fetched<DeviceState>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { devices.closeDevice() }
        onClose()
    }
    LaunchedEffect(reload) {
        while (true) {
            val fetched = devices.deviceState()
            if (fetched is Fetched.Ready && fetched.value.gone) {
                onClose()
                return@LaunchedEffect
            }
            // Keep the last good read through a hiccup.
            if (fetched is Fetched.Ready || device !is Fetched.Ready) device = fetched
            delay(500)
        }
    }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        if (message != null) {
            delay(4_000)
            message = null
        }
    }
    val act: (DeviceControl) -> Unit = { control ->
        scope.launch {
            val result = devices.deviceControl(control)
            if (result is CommandResult.Failed) message = result.reason
            reload++
        }
    }
    var tuning by remember { mutableStateOf(false) }
    val targets = remember { DropTargets() }
    var dragging by remember { mutableStateOf<DrawerItem?>(null) }
    var dragAt by remember { mutableStateOf(Offset.Zero) }
    var panelAt by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = null, indication = null, onClick = close),
    )
    Surface(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 460.dp).fillMaxHeight()
            .onGloballyPositioned { panelAt = it.positionInRoot() }
            // Taps on the panel don't reach the dim (which would close it).
            .clickable(interactionSource = null, indication = null, onClick = {}),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
    ) {
        Box {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (val result = device) {
                    null -> LoadingRow("Reading the device...")
                    is Fetched.Failed -> FailedRow(result.reason, "Close", close)
                    is Fetched.Ready -> {
                        val d = result.value
                        DeviceHeader(d, iconUrl, act, close)
                        message?.let { Text(it, color = ErrorText, style = MaterialTheme.typography.bodySmall) }
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Controls(d, iconUrl, act, targets, dragging, dragAt, onTune = { tuning = true })
                            }
                            Drawer(d.drawer, d, iconUrl, Modifier.width(196.dp).fillMaxHeight(),
                                onInsert = { act(DeviceControl.Insert(it.id)) },
                                onDrag = { item, at -> dragging = item; dragAt = at },
                                onDrop = { item ->
                                    // Once per drag: the row leaving the list after the insert ends its gesture again.
                                    if (dragging == item && targets.rects[item.fits]?.contains(dragAt) == true) act(DeviceControl.Insert(item.id))
                                    dragging = null
                                },
                            )
                        }
                    }
                }
            }
            val ready = (device as? Fetched.Ready)?.value
            if (tuning && ready?.range != null) {
                NumberPad(ready, ready.range, onDone = { tuning = false }, onTune = { act(DeviceControl.Channel(it)) },
                    onSave = {
                        act(DeviceControl.Channel(it))
                        act(DeviceControl.AddPreset(it))
                    })
            }
            // The dragged item follows the finger.
            dragging?.let { item ->
                Box(Modifier.offset { (dragAt - panelAt).let { IntOffset(it.x.roundToInt() - 40, it.y.roundToInt() - 30) } }.width(180.dp)) {
                    DrawerRow(item, iconUrl, lifted = true)
                }
            }
        }
    }
}

@Composable
private fun DeviceHeader(d: DeviceState, iconUrl: (String) -> String, act: (DeviceControl) -> Unit, onClose: () -> Unit) {
    Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GameIcon(d.icon, iconUrl, 36.dp)
        Column(Modifier.weight(1f)) {
            Text(d.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val where = when (d.where) {
                DeviceWhere.HAND -> "In your hand"
                DeviceWhere.BACK -> "On your back"
                DeviceWhere.FLOOR -> "On the floor"
                DeviceWhere.VEHICLE -> "In the car"
                null -> null
            }
            val facts = listOfNotNull(where, d.grid?.text, d.twoWay?.let { t -> "two-way" + (t.range?.let { ", $it m" } ?: "") })
            if (facts.isNotEmpty()) {
                Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = if (d.grid?.power == false) Danger else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        if (d.on) {
            FilledTonalButton(onClick = { act(DeviceControl.Power) }) { Text("Turn Off") }
        } else {
            Button(onClick = { act(DeviceControl.Power) }, enabled = d.powered) { Text("Turn On") }
        }
        TextButton(onClick = onClose) { Text("Close") }
    }
}

/** The left column: battery, volume, microphone, the radio's display, channels, the media slot. */
@Composable
private fun ColumnScope.Controls(
    d: DeviceState,
    iconUrl: (String) -> String,
    act: (DeviceControl) -> Unit,
    targets: DropTargets,
    dragging: DrawerItem?,
    dragAt: Offset,
    onTune: () -> Unit,
) {
    val working = d.on && d.powered
    d.battery?.let { battery ->
        Row(Modifier.height(SlotSize), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Battery", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val level = if (battery.has) battery.level else 0f
            FillMeter(level, if (level < 0.15f) Danger else Good, Modifier.weight(1f))
            Text(if (battery.has) "${(level * 100).roundToInt()}%" else "None", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slot(DrawerFit.BATTERY, if (battery.has) "Item_Battery" else null, "Item_Battery", iconUrl, targets, dragging, dragAt,
                onClick = { if (battery.has) act(DeviceControl.BatteryOut) })
        }
    }
    VolumeRow(d, iconUrl, act, targets, dragging, dragAt, enabled = working)
    d.twoWay?.let { twoWay ->
        Row(Modifier.height(SlotSize).alpha(if (working) 1f else 0.38f), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Switch(checked = !twoWay.micMuted, onCheckedChange = { act(DeviceControl.Microphone(muted = !it)) }, enabled = working)
            Text(if (twoWay.micMuted) "Microphone off" else "Microphone on")
        }
    }
    d.range?.let { RadioDisplay(d, onTune, enabled = working) }
    if (d.presets.isNotEmpty() || d.range != null) {
        if (d.tv) Text("Channel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).alpha(if (working) 1f else 0.38f),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (d.presets.isEmpty()) {
                Text("No presets yet: Tune… to a frequency, then Save preset.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val tuned = d.tunedPreset
            d.presets.forEach { preset ->
                ChannelRow(preset, current = preset == tuned, showFrequency = !d.tv, enabled = working,
                    onClick = { act(DeviceControl.Channel(preset.frequency)) })
            }
        }
    } else {
        Box(Modifier.weight(1f))
    }
    d.media?.let { MediaRow(it, d, iconUrl, act, targets, dragging, dragAt) }
}

@Composable
private fun VolumeRow(
    d: DeviceState,
    iconUrl: (String) -> String,
    act: (DeviceControl) -> Unit,
    targets: DropTargets,
    dragging: DrawerItem?,
    dragAt: Offset,
    enabled: Boolean,
) {
    // Unmuting goes back to the volume it had (RWMVolume remembers it the same way).
    var lastVolume by remember { mutableFloatStateOf(0.5f) }
    if (d.volume > 0f) lastVolume = d.volume
    val muted = d.volume <= 0f
    Row(Modifier.height(SlotSize), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(SlotSize).alpha(if (enabled) 1f else 0.38f)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) { act(if (muted) DeviceControl.Unmute(lastVolume) else DeviceControl.Mute) },
            contentAlignment = Alignment.Center,
        ) { Speaker(muted, MaterialTheme.colorScheme.onSurface) }
        Row(Modifier.weight(1f).fillMaxHeight().alpha(if (enabled) 1f else 0.38f), horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom) {
            val on = (d.volume * VOLUME_STEPS).roundToInt()
            for (step in 1..VOLUME_STEPS) {
                Box(
                    Modifier.weight(1f).fillMaxHeight((10f + step * 2.4f) / 34f)
                        .background(if (step <= on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            RoundedCornerShape(3.dp))
                        .clickable(enabled = enabled) { act(DeviceControl.Volume(step / VOLUME_STEPS.toFloat())) },
                )
            }
        }
        d.headphones?.let { phones ->
            Slot(DrawerFit.HEADPHONES, phones.icon, "Item_Headphones", iconUrl, targets, dragging, dragAt,
                onClick = { if (phones.name != null) act(DeviceControl.HeadphonesOut) })
        }
    }
}

/** A speaker, crossed out when muted. */
@Composable
private fun Speaker(muted: Boolean, color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val u = size.width / 20f
        val body = Path().apply {
            moveTo(3 * u, 7.5f * u); lineTo(6 * u, 7.5f * u); lineTo(10 * u, 4 * u); lineTo(10 * u, 16 * u)
            lineTo(6 * u, 12.5f * u); lineTo(3 * u, 12.5f * u); close()
        }
        drawPath(body, color)
        val stroke = Stroke(1.6f * u, cap = StrokeCap.Round)
        if (muted) {
            drawLine(color, Offset(13 * u, 7.5f * u), Offset(18 * u, 12.5f * u), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(18 * u, 7.5f * u), Offset(13 * u, 12.5f * u), stroke.width, StrokeCap.Round)
        } else {
            drawArc(color, -50f, 100f, false, Offset(9 * u, 6 * u), Size(8 * u, 8 * u), style = stroke)
            drawArc(color, -50f, 100f, false, Offset(8 * u, 3 * u), Size(14 * u, 14 * u), style = stroke)
        }
    }
}

/**
 * A slot that takes something from the drawer ([fit]): [icon] of what's in it (tap to take it out),
 * or [ghost] faintly when empty. Lights up while a matching item is dragged.
 */
@Composable
private fun Slot(
    fit: DrawerFit,
    icon: String?,
    ghost: String,
    iconUrl: (String) -> String,
    targets: DropTargets,
    dragging: DrawerItem?,
    dragAt: Offset,
    onClick: () -> Unit,
) {
    val rect = targets.rects[fit]
    val wanted = dragging?.fits == fit
    val over = wanted && rect?.contains(dragAt) == true
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.size(SlotSize)
            .onGloballyPositioned { targets.rects[fit] = it.boundsInRoot() }
            .background(
                when {
                    over -> Good.copy(alpha = 0.2f)
                    icon != null -> MaterialTheme.colorScheme.surfaceContainerHigh
                    else -> Color.Transparent
                }, shape,
            )
            .then(
                if (wanted || icon != null) Modifier.border(BorderStroke(if (wanted) 2.dp else 1.dp, if (wanted) Good else MaterialTheme.colorScheme.outline), shape)
                else Modifier.dashedBorder(MaterialTheme.colorScheme.outline)
            )
            .clickable(enabled = icon != null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.alpha(if (icon != null) 1f else 0.3f)) { GameIcon(icon ?: ghost, iconUrl, 26.dp) }
    }
}

/** An 8dp-rounded dashed outline: an empty slot. */
private fun Modifier.dashedBorder(color: Color): Modifier = drawBehind {
    val stroke = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
    drawRoundRect(color, cornerRadius = CornerRadius(8.dp.toPx()), style = stroke)
}

@Composable
private fun RadioDisplay(d: DeviceState, onTune: () -> Unit, enabled: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).background(LcdBack, RoundedCornerShape(8.dp)).border(1.dp, LcdEdge, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(d.channel?.let(Frequency::format) ?: "--.-", fontFamily = FontFamily.Monospace, fontSize = 22.sp, color = LcdText)
        Text("MHz", fontSize = 11.sp, color = LcdDim)
        Text(if (d.media?.playing == true) d.media.name ?: "" else d.channelName ?: "", Modifier.weight(1f), fontSize = 13.sp,
            color = LcdText.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        OutlinedButton(onClick = onTune, enabled = enabled, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(30.dp)) {
            Text("Tune…")
        }
    }
}

@Composable
private fun ChannelRow(preset: Preset, current: Boolean, showFrequency: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().height(38.dp)
            .background(if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, shape)
            .then(if (current) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(enabled = enabled && !current, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(preset.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (current) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
        if (showFrequency) Text(Frequency.format(preset.frequency), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else if (current) Text("▶", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/** The CD or VHS slot: what's in it (marked while it plays, since then it's what's on), Play/Stop, eject. */
@Composable
private fun MediaRow(
    media: MediaSlot,
    d: DeviceState,
    iconUrl: (String) -> String,
    act: (DeviceControl) -> Unit,
    targets: DropTargets,
    dragging: DrawerItem?,
    dragAt: Offset,
) {
    val rect = targets.rects[DrawerFit.MEDIA]
    val wanted = dragging?.fits == DrawerFit.MEDIA
    val over = wanted && rect?.contains(dragAt) == true
    val shape = RoundedCornerShape(10.dp)
    val icon = if (media.kind == MediaKind.VHS) "Item_Cassette3" else "Item_Disc"
    val has = media.name != null
    Row(
        Modifier.fillMaxWidth().height(54.dp)
            .onGloballyPositioned { targets.rects[DrawerFit.MEDIA] = it.boundsInRoot() }
            .background(
                when {
                    over -> Good.copy(alpha = 0.15f)
                    media.playing -> MaterialTheme.colorScheme.primaryContainer
                    has -> MaterialTheme.colorScheme.surfaceContainer
                    else -> Color.Transparent
                }, shape,
            )
            .then(
                when {
                    wanted -> Modifier.border(2.dp, Good, shape)
                    media.playing -> Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape)
                    has -> Modifier
                    else -> Modifier.dashedBorder(MaterialTheme.colorScheme.outline)
                }
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.alpha(if (has) 1f else 0.35f)) { GameIcon(icon, iconUrl, 32.dp) }
        Column(Modifier.weight(1f)) {
            Text(media.name ?: media.empty, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = when {
                    media.playing -> MaterialTheme.colorScheme.onPrimaryContainer
                    has -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                })
            val sub = when {
                media.playing -> "Playing"
                has -> "Stopped"
                media.kind == MediaKind.VHS -> "Drop a tape here"
                else -> "Drop a CD here"
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalButton(onClick = { act(DeviceControl.Play) }, enabled = has && d.on && d.powered,
            contentPadding = PaddingValues(horizontal = 14.dp)) { Text(if (media.playing) "Stop" else "Play") }
        if (has) TextButton(onClick = { act(DeviceControl.Eject) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(40.dp)) { Text("⏏") }
    }
}

/** What fits the device, on you and per container around. Tap to put it in; hold and drag onto its slot. */
@Composable
private fun Drawer(
    groups: List<DrawerGroup>,
    d: DeviceState,
    iconUrl: (String) -> String,
    modifier: Modifier,
    onInsert: (DrawerItem) -> Unit,
    onDrag: (DrawerItem, Offset) -> Unit,
    onDrop: (DrawerItem) -> Unit,
) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(8.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (groups.isEmpty()) {
                val what = listOfNotNull(
                    d.media?.let { if (it.kind == MediaKind.VHS) "tapes" else "CDs" },
                    "batteries".takeIf { d.battery != null },
                    "headphones".takeIf { d.headphones != null },
                )
                Text(if (what.isEmpty()) "Nothing goes in this one." else "No ${what.joinToString(", ")} on you or nearby.",
                    Modifier.padding(4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            groups.forEach { group ->
                Text(if (group.onYou) "On you" else group.name ?: "Nearby", Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                group.items.forEach { item ->
                    var rowAt by remember { mutableStateOf(Offset.Zero) }
                    // Lifting the finger after a drag also counts as a tap on the row: not a second insert.
                    var droppedAt by remember { mutableLongStateOf(0L) }
                    Box(
                        Modifier.onGloballyPositioned { rowAt = it.positionInRoot() }
                            // Before the drag detector, so the drag's events reach the drag first.
                            .clickable { if (SystemClock.uptimeMillis() - droppedAt > 400) onInsert(item) }
                            .pointerInput(item.id) {
                                var at = Offset.Zero
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { at = rowAt + it; onDrag(item, at) },
                                    onDrag = { change, amount -> change.consume(); at += amount; onDrag(item, at) },
                                    onDragEnd = { droppedAt = SystemClock.uptimeMillis(); onDrop(item) },
                                    onDragCancel = { droppedAt = SystemClock.uptimeMillis(); onDrop(item) },
                                )
                            },
                    ) { DrawerRow(item, iconUrl, lifted = false) }
                }
            }
        }
    }
}

@Composable
private fun DrawerRow(item: DrawerItem, iconUrl: (String) -> String, lifted: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (lifted) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = if (lifted) 8.dp else 0.dp,
        modifier = Modifier.fillMaxWidth().height(42.dp),
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameIcon(item.icon, iconUrl, 28.dp)
            Text(item.name, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.count > 1) Text("×${item.count}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Tuning without a keyboard: the radio's display, its range, a number pad. Tune in goes there;
 * Save preset also adds it to the list. Both wait until the number is inside the radio's range.
 */
@Composable
private fun BoxScope.NumberPad(
    d: DeviceState,
    range: IntRange,
    onDone: () -> Unit,
    onTune: (Int) -> Unit,
    onSave: (Int) -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    val frequency = Frequency.parse(typed)
    val inRange = frequency != null && frequency in range
    val roomForPreset = d.maxPresets == null || d.presets.size < d.maxPresets
    Box(
        Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = null, indication = null, onClick = onDone),
    )
    Surface(
        Modifier.align(Alignment.Center).width(300.dp).clickable(interactionSource = null, indication = null, onClick = {}),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth().height(48.dp).background(LcdBack, RoundedCornerShape(8.dp)).border(1.dp, LcdEdge, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(typed.ifEmpty { d.channel?.let(Frequency::format) ?: "" }, fontFamily = FontFamily.Monospace, fontSize = 28.sp,
                    color = if (typed.isEmpty()) LcdDim else LcdText)
                Text("MHz", fontSize = 11.sp, color = LcdDim)
            }
            Text("${Frequency.format(range.first)} – ${Frequency.format(range.last)} MHz", Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.bodySmall,
                color = if (frequency != null && !inRange) Danger else MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "⌫")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { key -> PadKey(key) { typed = Frequency.type(typed, key) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { frequency?.let(onSave); onDone() }, enabled = inRange && roomForPreset,
                    modifier = Modifier.weight(1f)) { Text("Save preset") }
                Button(onClick = { frequency?.let(onTune); onDone() }, enabled = inRange, modifier = Modifier.weight(1f)) { Text("Tune in") }
            }
        }
    }
}

@Composable
private fun RowScope.PadKey(key: String, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).height(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(key, fontFamily = if (key == "⌫") FontFamily.Default else FontFamily.Monospace, fontSize = 20.sp,
            color = if (key == "⌫") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}
