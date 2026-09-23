package dev.zomboidds.companion.ui

import androidx.compose.foundation.layout.BoxScope
import dev.zomboidds.companion.domain.TimeState
import dev.zomboidds.companion.domain.GameSpeed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import dev.zomboidds.companion.domain.Moodle
import dev.zomboidds.companion.domain.Moodles
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.zomboidds.companion.domain.BodyPartStatus
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.GameControls
import dev.zomboidds.companion.domain.GameState
import dev.zomboidds.companion.domain.HealthTone
import dev.zomboidds.companion.domain.ItemActions
import dev.zomboidds.companion.domain.ItemMenuResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "You": the game's moodles on top, then the body with its injuries. Tapping a moodle shows the
 * game's description of it; tapping a body part opens the game's own treatment menu for it.
 */
@Composable
fun StatusScreen(state: GameState, controls: GameControls, actions: ItemActions, iconUrl: (String) -> String) {
    val scope = rememberCoroutineScope()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    val parts = state.health?.parts
    // Follow the part through updates; it's gone once healed.
    val selected = parts?.firstOrNull { it.id == selectedId }

    LaunchedEffect(failure) {
        if (failure != null) {
            delay(4_000)
            failure = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.moodles?.let { MoodleStrip(it, iconUrl) }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // The game's body silhouette, hurt parts tinted, health in its corner.
                Box(
                    Modifier.width(160.dp).fillMaxHeight()
                        .background(Color(0xFF1B1920), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFF2A2730), RoundedCornerShape(10.dp)),
                ) {
                    BodySilhouette(parts.orEmpty(), state.health?.female == true, iconUrl,
                        Modifier.fillMaxSize().padding(top = 34.dp, bottom = 10.dp, start = 10.dp, end = 10.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Health", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f))
                        Text(state.player?.health?.let { "%.0f".format(it) } ?: "?", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    failure?.let { Text(it, color = ErrorText) }
                    when {
                        parts == null -> Hint("Waiting for the game...")
                        parts.isEmpty() -> Hint("No injuries")
                        else -> {
                            val (open, treated) = parts.partition { it.needsTreatment }
                            if (open.isNotEmpty()) {
                                Section("Needs treatment")
                                open.forEach { InjuryRow(it, onClick = { selectedId = it.id }) }
                            }
                            if (treated.isNotEmpty()) {
                                Section("Treated")
                                treated.forEach { InjuryRow(it, dimmed = true, onClick = { selectedId = it.id }) }
                            }
                        }
                    }
                    // A mod from before moodles: its hunger/thirst/... bars instead.
                    if (state.moodles == null) state.player?.let { PlayerCard(it, showHealth = false) }
                }
            }
        }
        if (selected != null) {
            TreatmentPanel(
                selected,
                time = state.time,
                onUnpause = { scope.launch { controls.setSpeed(GameSpeed.PLAY) } },
                loadMenu = { controls.bodyPartMenu(selected.id) },
                onSelect = { menuId, optionId ->
                    selectedId = null
                    scope.launch {
                        val result = actions.selectMenuOption(menuId, optionId)
                        if (result is CommandResult.Failed) failure = "${selected.name}: ${result.reason}"
                    }
                },
                onClose = { selectedId = null },
            )
        }
    }
}

/**
 * The game's moodles as its moodle column draws them: the icon on the round background in the
 * game's colour for it, plus its name. Tapping one shows the game's description (its tooltip).
 */
@Composable
private fun MoodleStrip(moodles: Moodles, iconUrl: (String) -> String) {
    var openId by remember { mutableStateOf<String?>(null) }
    val open = moodles.list.firstOrNull { it.id == openId }
    if (moodles.list.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            moodles.list.forEach { moodle ->
                Column(
                    Modifier.width(60.dp)
                        .clickable { openId = if (openId == moodle.id) null else moodle.id }
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MoodleIcon(moodle, moodles, iconUrl, selected = moodle.id == openId)
                    Text(
                        moodle.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        open?.let {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(it.name) }
                    it.description?.takeIf { d -> d.isNotBlank() }?.let { d -> append(": $d") }
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun MoodleIcon(moodle: Moodle, moodles: Moodles, iconUrl: (String) -> String, selected: Boolean) {
    val tint = moodle.color.takeIf { it.size >= 3 }?.let { Color(it[0], it[1], it[2]) } ?: Color.Gray
    Box(Modifier.size(44.dp).then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)) {
        // Drawn like the game: the white background multiplied by the moodle's colour, its outline, the icon.
        moodles.background?.let {
            AsyncImage(iconUrl(it), null, Modifier.fillMaxSize(), colorFilter = ColorFilter.tint(tint, BlendMode.Modulate))
        }
        moodles.border?.let { AsyncImage(iconUrl(it), null, Modifier.fillMaxSize()) }
        moodle.icon?.let { AsyncImage(iconUrl(it), moodle.name, Modifier.fillMaxSize()) }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One body part: a coloured edge (its most urgent line), its lines on one row, Treat ›. */
@Composable
private fun InjuryRow(part: BodyPartStatus, dimmed: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .background(Color(0xFF1C1A21), RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
            .clickable(onClick = onClick)
            .alpha(if (dimmed) 0.75f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(color(part.tone)))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(part.name, style = MaterialTheme.typography.titleSmall)
            HealthLinesInline(part)
        }
        Text("Treat ›", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
    }
}

/** "Scratched · Bleeding", each line in its own colour. */
@Composable
private fun HealthLinesInline(part: BodyPartStatus) {
    Text(healthLines(part), style = MaterialTheme.typography.bodyMedium)
}

/** "Scratched · Bleeding", each line in the game's colour for it. */
@Composable
private fun healthLines(part: BodyPartStatus) = buildAnnotatedString {
    part.lines.forEachIndexed { index, line ->
        if (index > 0) withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(" · ") }
        withStyle(SpanStyle(color = color(line.tone))) { append(line.text) }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The game's treatment menu for one body part, fetched when the panel opens. */
@Composable
private fun BoxScope.TreatmentPanel(
    part: BodyPartStatus,
    time: TimeState?,
    onUnpause: () -> Unit,
    loadMenu: suspend () -> ItemMenuResult,
    onSelect: (menuId: String, optionId: String) -> Unit,
    onClose: () -> Unit,
) = BottomPanel(onDismiss = onClose) {
    // One line: the list behind the panel already shows the rest.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(part.name) }
                if (part.lines.isNotEmpty()) {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(" · ") }
                    append(healthLines(part))
                }
            },
            Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onClose) { Text("Close") }
    }
    HorizontalDivider()
    // The game builds no treatment menu while paused: wait, and load it by itself once it runs.
    LoadingGameMenu(key = part.id, load = loadMenu, onSelect = onSelect, enabled = time?.speed != GameSpeed.PAUSED) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (time?.gameMenuOpen == true) "Close the game's menu, then unpause to treat" else "Unpause to treat",
                Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (time?.canChange == true && !time.gameMenuOpen) TextButton(onClick = onUnpause) { Text("Unpause") }
        }
    }
}

/** The game's own colours for health lines. */
@Composable
private fun color(tone: HealthTone) = when (tone) {
    HealthTone.BAD -> Danger
    HealthTone.GOOD -> Good
    HealthTone.WARN -> Warning
    HealthTone.NEUTRAL -> MaterialTheme.colorScheme.onSurface
}
