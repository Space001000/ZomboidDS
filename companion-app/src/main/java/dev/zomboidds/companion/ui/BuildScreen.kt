package dev.zomboidds.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zomboidds.companion.domain.BuildGroup
import dev.zomboidds.companion.domain.BuildList
import dev.zomboidds.companion.domain.Building
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.Fetched
import dev.zomboidds.companion.domain.Placing
import dev.zomboidds.companion.domain.RecipeDetails
import dev.zomboidds.companion.domain.missing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The game's build window on the bottom screen: what you can build as a grid, a thing's versions
 * (Shoddy, Poor, Good) on one tile, filtered by category. Tap one for what it needs, pick the
 * version, and Place: the game's own cursor comes up on the top screen, and the controller places it.
 * While it's up, a strip in place of the filters says what you're placing, with Stop.
 * [changes]: anything that changes what's in reach (inventory, containers); the list follows it.
 */
@Composable
fun BuildScreen(building: Building, iconUrl: (String) -> String, changes: Any?, placing: Placing?) {
    var list by remember { mutableStateOf<Fetched<BuildList>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(changes, reload) {
        if (list != null) delay(500) // several changes in a row (a placement, a transfer): ask once
        list = building.buildRecipes()
    }
    var canBuildOnly by rememberSaveable { mutableStateOf(true) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<String?>(null) } // a group key
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        if (message != null) {
            delay(4_000)
            message = null
        }
    }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        when (val result = list) {
            null -> LoadingRow("Loading the game's build recipes...")
            is Fetched.Failed -> FailedRow(result.reason, "Retry") { reload++ }
            is Fetched.Ready -> {
                val groups = result.value.groups
                val inCategory = groups.filter { category == null || it.category == category }
                val shown = inCategory.filter { !canBuildOnly || it.canBuild }
                Column(verticalArrangement = Arrangement.spacedBy(PaneSpacing)) {
                    if (placing != null) {
                        PlacingStrip(placing, iconUrl, onStop = {
                            scope.launch {
                                (building.stopPlacing() as? CommandResult.Failed)?.let { message = it.reason }
                            }
                        })
                        PlacingHelp(placing)
                    } else {
                        RecipeFilters("Can build", canBuildOnly, { canBuildOnly = it }, result.value.categories, category, { category = it })
                        Text(
                            if (canBuildOnly) "You can build ${shown.size} of ${inCategory.size}" else "${shown.size} to build",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    message?.let { Text(it, color = ErrorText, style = MaterialTheme.typography.bodySmall) }
                    if (shown.isEmpty()) {
                        Text(
                            if (canBuildOnly) "Nothing you can build with what's in reach. Turn off \"Can build\" to see the rest."
                            else "Nothing to build here.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 64.dp),
                        horizontalArrangement = Arrangement.spacedBy(Gap),
                        verticalArrangement = Arrangement.spacedBy(Gap),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(shown, key = { it.key }) { group ->
                            val placingThis = placing != null && group.versions.any { it.id == placing.id }
                            BuildTile(group, iconUrl, canBuildOnly, selected = group.key == selected || placingThis,
                                onClick = { selected = group.key })
                        }
                    }
                }
                groups.firstOrNull { it.key == selected }?.let { group ->
                    BuildPanel(
                        building, group, iconUrl, changes,
                        onPlace = { id ->
                            scope.launch {
                                when (val placed = building.place(id)) {
                                    CommandResult.Ok -> selected = null
                                    is CommandResult.Failed -> message = placed.reason
                                }
                            }
                        },
                        onClose = { selected = null },
                    )
                }
            }
        }
    }
}

/**
 * A thing to build, like an inventory tile; dimmed when no version can be built. The corner says
 * how many versions it has (how many you can build, with "Can build" on).
 */
@Composable
private fun BuildTile(group: BuildGroup, iconUrl: (String) -> String, canBuildOnly: Boolean, selected: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, border = selectedBorder(selected), modifier = Modifier.height(TileHeight)) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(3.dp).alpha(if (group.canBuild) 1f else 0.4f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(38.dp), contentAlignment = Alignment.Center) {
                    GameIcon(group.icon, iconUrl, 36.dp)
                }
                Text(group.name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            val count = if (canBuildOnly) group.buildable else group.versions.size
            if (count > 1) {
                Text(
                    "$count", Modifier.align(Alignment.TopEnd).padding(3.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp)).padding(horizontal = 3.dp),
                    fontSize = 9.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** What a thing needs, its versions as chips, and Place. */
@Composable
private fun BoxScope.BuildPanel(
    building: Building,
    group: BuildGroup,
    iconUrl: (String) -> String,
    changes: Any?,
    onPlace: (id: String) -> Unit,
    onClose: () -> Unit,
) {
    var versionId by remember(group.key) { mutableStateOf(group.preferred.id) }
    // Fetched again when what's in reach changes, so the ✓ / ✗ stay true.
    val details by produceState<Fetched<RecipeDetails>?>(null, versionId, changes) { value = building.buildRecipe(versionId) }
    BottomPanel(onDismiss = onClose, footer = { (details as? Fetched.Ready)?.value?.let { PlaceRow(it, onPlace) } }) {
        val recipe = (details as? Fetched.Ready)?.value
        RecipeHeader(group.icon, recipe?.name ?: group.versions.first { it.id == versionId }.name, recipe, iconUrl, onClose)
        if (group.versions.size > 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                group.versions.forEach { version ->
                    FilterChip(
                        selected = version.id == versionId,
                        onClick = { versionId = version.id },
                        label = {
                            Text(buildAnnotatedString {
                                append(version.version ?: version.name)
                                version.skill?.let {
                                    withStyle(SpanStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                        append("  ${it.name} ${it.level}")
                                    }
                                }
                            })
                        },
                        modifier = Modifier.alpha(if (version.canBuild) 1f else 0.55f),
                    )
                }
            }
        }
        HorizontalDivider()
        when (val result = details) {
            null -> LoadingRow("Loading the recipe...")
            is Fetched.Failed -> Text(result.reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
            is Fetched.Ready -> {
                RecipeInputs("Materials", result.value, iconUrl)
            }
        }
    }
}

/** Place, and what's missing or how the game's cursor works. Stays in view below the materials. */
@Composable
private fun PlaceRow(recipe: RecipeDetails, onPlace: (id: String) -> Unit) {
    val missing = recipe.missing
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = recipe.canCraft, onClick = { onPlace(recipe.id) }) { Text("Place") }
        if (missing.isNotEmpty()) {
            Text("Missing: " + missing.joinToString(", "), color = Danger,
                style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        } else {
            Text("On the top screen: d-pad moves · LB/RB turn · A builds · B stops",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** In place of the filters while the game's cursor is up: what you're placing, and Stop. */
@Composable
private fun PlacingStrip(placing: Placing, iconUrl: (String) -> String, onStop: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(40.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
            .padding(start = 6.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GameIcon(placing.icon, iconUrl, 28.dp)
        Text(
            buildAnnotatedString {
                append("Placing ${placing.name}")
                withStyle(SpanStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))) {
                    append("  on the top screen")
                }
            },
            Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        OutlinedButton(onClick = onStop) { Text("Stop") }
    }
}

/** The game's controls for its cursor, or why it won't place. */
@Composable
private fun PlacingHelp(placing: Placing) {
    if (placing.blocked) {
        Text(
            if (placing.missing.isEmpty()) "Can't place it now: something is missing"
            else "Can't place it now. Missing: " + placing.missing.joinToString(", "),
            color = Danger, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    } else {
        Text(
            buildAnnotatedString {
                listOf("D-pad" to "move", "LB / RB" to "turn", "A" to "build", "B" to "stop", "Y" to "back to you")
                    .forEachIndexed { i, (key, what) ->
                        if (i > 0) append(" · ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)) { append(key) }
                        append(" $what")
                    }
            },
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
        )
    }
}
