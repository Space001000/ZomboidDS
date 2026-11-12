package dev.zomboidds.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.ExerciseState
import dev.zomboidds.companion.domain.ExerciseType
import dev.zomboidds.companion.domain.Fetched
import dev.zomboidds.companion.domain.GameSpeed
import dev.zomboidds.companion.domain.GameWindows
import dev.zomboidds.companion.domain.TimeState
import dev.zomboidds.companion.domain.InfoCard
import dev.zomboidds.companion.domain.InfoRow
import dev.zomboidds.companion.domain.Question
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val WaterBlue = Color(0xFF5AA0E0)

/** Reads in a row a question may fail before the queue moves on without it. */
private const val UNREADABLE_TRIES = 6

/**
 * One of the game's yes/no questions as a card over the tab. Its first button (Yes, OK) is the
 * filled one. Read twice a second: answered with the controller, it goes away here too. Tapping the
 * dim doesn't dismiss it: the game waits for an answer.
 */
@Composable
internal fun BoxScope.QuestionCard(windows: GameWindows, id: Long, onDone: () -> Unit) {
    var question by remember(id) { mutableStateOf<Question?>(null) }
    var failure by remember(id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id) {
        var unreadable = 0
        while (true) {
            when (val fetched = windows.question(id)) {
                is Fetched.Ready -> {
                    if (fetched.value.gone) {
                        onDone()
                        return@LaunchedEffect
                    }
                    question = fetched.value
                    unreadable = 0
                }
                is Fetched.Failed -> {
                    failure = fetched.reason
                    // Never shown and never answered here: let the next question through (the game
                    // shows this one itself once the app goes; the controller answers it meanwhile).
                    if (question == null && ++unreadable >= UNREADABLE_TRIES) {
                        onDone()
                        return@LaunchedEffect
                    }
                }
            }
            delay(500)
        }
    }
    val q = question ?: return
    DimmedCard(Modifier.align(Alignment.Center), maxWidth = 340.dp, onDismiss = {}) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(q.text, style = MaterialTheme.typography.bodyLarge)
            failure?.let { Text(it, color = ErrorText, style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                // The others (No) first, the first button (Yes / OK) last and filled.
                (q.buttons.drop(1) + q.buttons.take(1)).forEach { button ->
                    val answer: () -> Unit = {
                        scope.launch {
                            // Answered: done. Refused (already answered with the controller): say why;
                            // the next read finds it gone and closes the card.
                            when (val result = windows.answer(q.id, button.id)) {
                                CommandResult.Ok -> onDone()
                                is CommandResult.Failed -> failure = result.reason
                            }
                        }
                    }
                    if (button == q.buttons.first()) Button(onClick = answer) { Text(button.label) }
                    else TextButton(onClick = answer) { Text(button.label) }
                }
            }
        }
    }
}

/**
 * One of the game's info windows as a card: its lines as rows, percentages with a bar. It stays
 * while the game would keep its window (read twice a second), and closes with Close or a tap outside.
 */
@Composable
internal fun BoxScope.InfoCardView(windows: GameWindows, iconUrl: (String) -> String, onClose: () -> Unit) {
    var info by remember { mutableStateOf<Fetched<InfoCard>?>(null) }
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { windows.closeInfo() }
        onClose()
    }
    LaunchedEffect(Unit) {
        while (true) {
            val fetched = windows.infoCard()
            if (fetched is Fetched.Ready && fetched.value.gone) {
                onClose()
                return@LaunchedEffect
            }
            if (fetched is Fetched.Ready || info !is Fetched.Ready) info = fetched
            delay(500)
        }
    }
    val current = info ?: return
    DimmedCard(Modifier.align(Alignment.Center), maxWidth = 340.dp, onDismiss = close) {
        Column(Modifier.heightIn(max = 380.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (current) {
                is Fetched.Failed -> FailedRow(current.reason, "Close", close)
                is Fetched.Ready -> {
                    val card = current.value
                    Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GameIcon(card.icon, iconUrl, 36.dp)
                        Column(Modifier.weight(1f)) {
                            Text(card.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            card.status?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        TextButton(onClick = close) { Text("Close") }
                    }
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        card.rows.forEach { InfoRowView(it, iconUrl) }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRowView(row: InfoRow, iconUrl: (String) -> String) {
    val color = if (row.bad) Danger else MaterialTheme.colorScheme.onSurface
    when {
        // A heading ("Power Consumption", "Disease") or plain text.
        row.text != null || row.value == null && row.meter == null && !row.indent -> {
            Text(row.text ?: row.label.orEmpty(), color = if (row.bad) Danger else MaterialTheme.colorScheme.onSurfaceVariant,
                style = if (row.text != null) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
        }
        // What belongs to the row above: a powered thing and its use, a plant's pest.
        row.indent && row.icon == null -> Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp)) {
            Text(row.label.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(1f))
            row.value?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        else -> Row(
            Modifier.fillMaxWidth().heightIn(min = 34.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(start = if (row.indent) 24.dp else 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(row.label.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f))
            row.meter?.let { FillMeter(it, if (row.water) WaterBlue else if (it < 0.2f) Danger else Good, Modifier.width(90.dp)) }
            row.icon?.let { GameIcon(it, iconUrl, 22.dp) }
            row.value?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = color) }
        }
    }
}

/**
 * The game's exercise window as a bottom panel: its exercises with their regularity bars (greyed
 * without the barbell or dumbbell), what the picked one trains and strains, the minutes, Start, or
 * Stop and how far along while one runs. Start is greyed with the game's reason when you can't.
 */
@Composable
internal fun BoxScope.ExercisePanel(
    windows: GameWindows,
    iconUrl: (String) -> String,
    /** The game's speed, for > >> >>> while exercising; null or not changeable (multiplayer): no buttons. */
    time: TimeState? = null,
    onSpeed: (GameSpeed) -> Unit = {},
    onClose: () -> Unit,
) {
    var state by remember { mutableStateOf<Fetched<ExerciseState>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<String?>(null) }
    var minutes by remember { mutableIntStateOf(20) }
    var message by rememberTransientMessage()
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload) {
        while (true) {
            val fetched = windows.exercise()
            if (fetched is Fetched.Ready || state !is Fetched.Ready) state = fetched
            delay(1_000)
        }
    }
    val run: (suspend () -> CommandResult) -> Unit = { call ->
        scope.launch {
            val result = call()
            if (result is CommandResult.Failed) message = result.reason
            reload++
        }
    }
    SheetPanel(onDismiss = onClose) {
            when (val current = state) {
                null -> LoadingRow("Reading the exercises...")
                is Fetched.Failed -> FailedRow(current.reason, "Close", onClose)
                is Fetched.Ready -> {
                    val s = current.value
                    val running = s.running
                    // The running one is the picked one; else the player's pick, else the first open one.
                    val chosen = s.exercises.firstOrNull { it.id == (running?.id ?: picked) }
                        ?: s.exercises.firstOrNull { it.needs == null } ?: s.exercises.firstOrNull()
                    Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Exercise", style = MaterialTheme.typography.titleMedium)
                            Text(if (running != null) "Exercising" else "Pick one, then Start", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (running != null) {
                            FilledTonalButton(onClick = { run { windows.stopExercise() } }) { Text("Stop") }
                        } else {
                            Button(onClick = { chosen?.let { c -> run { windows.startExercise(c.id, minutes) } } },
                                enabled = s.canStart && chosen != null && chosen.needs == null) { Text("Start") }
                        }
                        TextButton(onClick = onClose) { Text("Close") }
                    }
                    message?.let { Text(it, color = ErrorText, style = MaterialTheme.typography.bodySmall) }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(Modifier.padding(start = 46.dp, end = 10.dp)) {
                                Text("Exercise", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f))
                                Text("Regularity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            s.exercises.forEach { exercise ->
                                ExerciseRow(exercise, iconUrl, selected = exercise == chosen,
                                    enabled = running == null && exercise.needs == null, onClick = { picked = exercise.id })
                            }
                        }
                        chosen?.let { ExerciseSide(it, s, minutes, onMinutes = { minutes = it }, time, onSpeed, Modifier.width(210.dp).fillMaxHeight()) }
                    }
                }
            }
    }
}

@Composable
private fun ExerciseRow(exercise: ExerciseType, iconUrl: (String) -> String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().height(40.dp).clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
            .alpha(if (exercise.needs != null) 0.38f else 1f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(26.dp)) { exercise.icon?.let { GameIcon(it, iconUrl, 26.dp) } }
        Column(Modifier.weight(1f)) {
            Text(exercise.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
            exercise.needs?.let { Text(it, fontSize = 11.sp, lineHeight = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        FillMeter(exercise.regularity, Good, Modifier.width(56.dp))
    }
}

@Composable
private fun ExerciseSide(
    exercise: ExerciseType,
    s: ExerciseState,
    minutes: Int,
    onMinutes: (Int) -> Unit,
    time: TimeState?,
    onSpeed: (GameSpeed) -> Unit,
    modifier: Modifier,
) {
    val running = s.running
    val steps = s.minutes.toList()
    val shown = running?.minutes ?: minutes.coerceIn(s.minutes.first, s.minutes.last)
    val index = steps.indexOf(shown)
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
        .verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(exercise.name, style = MaterialTheme.typography.titleSmall)
        exercise.tooltip?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        exercise.strain.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        HorizontalDivider()
        Text("Time (minutes)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundStepButton("‹", enabled = running == null && index > 0) { onMinutes(steps[index - 1]) }
            Text("$shown min", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            RoundStepButton("›", enabled = running == null && index in 0 until steps.lastIndex) { onMinutes(steps[index + 1]) }
        }
        if (running != null) {
            Text("${running.done} of ${running.minutes} min", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress = { if (running.minutes > 0) running.done / running.minutes.toFloat() else 0f },
                modifier = Modifier.fillMaxWidth())
            // To wait it out: the game's speeds, small, the current one lit.
            if (time?.speed != null && time.canChange) SpeedSteps(time.speed, onSpeed)
        }
        s.why?.let { if (running == null) Text(it, style = MaterialTheme.typography.bodySmall, color = Danger) }
        Spacer(Modifier.height(2.dp))
    }
}

/** > >> >>>: play, fast and faster, as the game's speed buttons; the current one filled. */
@Composable
private fun SpeedSteps(current: GameSpeed, onSpeed: (GameSpeed) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(GameSpeed.PLAY to ">", GameSpeed.FAST to ">>", GameSpeed.FASTER to ">>>").forEach { (speed, label) ->
            val on = speed == current
            Box(
                Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = { if (!on) onSpeed(speed) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
            }
        }
    }
}
