package dev.zomboidds.companion.ui

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
 * "You": injuries first while you're hurt (that's what matters then), then health and needs.
 * Tapping a body part opens the game's own treatment menu for it.
 */
@Composable
fun StatusScreen(state: GameState, controls: GameControls, actions: ItemActions) {
    val scope = rememberCoroutineScope()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    val parts = state.health
    // Follow the part through updates; it's gone once healed.
    val selected = parts?.firstOrNull { it.id == selectedId }
    val hurt = !parts.isNullOrEmpty()

    LaunchedEffect(failure) {
        if (failure != null) {
            delay(4_000)
            failure = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            failure?.let { Text(it, color = Color(0xFFE57373)) }
            if (hurt) InjuriesCard(parts, onPart = { selectedId = it.id })
            state.player?.let { PlayerCard(it) }
            if (!hurt) InjuriesCard(parts, onPart = { selectedId = it.id })
        }
        if (selected != null) {
            // Drawn in this window, not as a dialog: a new window could take focus from the game.
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(interactionSource = null, indication = null) { selectedId = null },
            )
            TreatmentPanel(
                selected,
                loadMenu = { controls.bodyPartMenu(selected.id) },
                onSelect = { menuId, optionId ->
                    selectedId = null
                    scope.launch {
                        val result = actions.selectMenuOption(menuId, optionId)
                        if (result is CommandResult.Failed) failure = "${selected.name}: ${result.reason}"
                    }
                },
                onClose = { selectedId = null },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Injuries as the game's health panel lists them: each body part with the game's own lines. */
@Composable
private fun InjuriesCard(parts: List<BodyPartStatus>?, onPart: (BodyPartStatus) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text("Injuries", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            when {
                parts == null -> Hint("Waiting for the game...")
                parts.isEmpty() -> Hint("No injuries")
                else -> parts.forEachIndexed { index, part ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable { onPart(part) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(part.name, style = MaterialTheme.typography.titleSmall)
                            HealthLines(part)
                        }
                        Text("Treat ›", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun HealthLines(part: BodyPartStatus) {
    part.lines.forEach { line ->
        Text(line.text, style = MaterialTheme.typography.bodyMedium, color = color(line.tone))
    }
}

/** The game's treatment menu for one body part, fetched when the panel opens. */
@Composable
private fun TreatmentPanel(
    part: BodyPartStatus,
    loadMenu: suspend () -> ItemMenuResult,
    onSelect: (menuId: String, optionId: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    var reload by remember { mutableIntStateOf(0) }
    val menu by produceState<ItemMenuResult?>(null, part.id, reload) {
        value = null
        value = loadMenu()
    }
    Card(modifier.fillMaxWidth()) {
        Column(
            Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(part.name, style = MaterialTheme.typography.titleMedium)
                    HealthLines(part)
                }
                TextButton(onClick = onClose) { Text("Close") }
            }
            HorizontalDivider()
            when (val result = menu) {
                null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Loading the game's options...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is ItemMenuResult.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(result.reason, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { reload++ }) { Text("Retry") }
                }
                is ItemMenuResult.Ready -> GameMenu(result.menu, onSelect = { optionId -> onSelect(result.menu.menuId, optionId) })
            }
        }
    }
}

/** The game's own colours for health lines. */
@Composable
private fun color(tone: HealthTone) = when (tone) {
    HealthTone.BAD -> Color(0xFFE35050)
    HealthTone.GOOD -> Color(0xFF6BD36B)
    HealthTone.WARN -> Color(0xFFFF9447)
    HealthTone.NEUTRAL -> MaterialTheme.colorScheme.onSurface
}
