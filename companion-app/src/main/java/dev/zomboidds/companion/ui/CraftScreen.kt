package dev.zomboidds.companion.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.Crafting
import dev.zomboidds.companion.domain.Fetched
import dev.zomboidds.companion.domain.RecipeDetails
import dev.zomboidds.companion.domain.RecipeInput
import dev.zomboidds.companion.domain.RecipeList
import dev.zomboidds.companion.domain.RecipeSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The game's crafting window on the bottom screen: its recipes as a grid (by default only what you
 * can make now), filtered by category; tap one for what it needs and makes, and Craft.
 * [changes]: anything that changes what's in reach (inventory, containers); the list follows it.
 */
@Composable
fun CraftScreen(crafting: Crafting, iconUrl: (String) -> String, changes: Any?) {
    var list by remember { mutableStateOf<Fetched<RecipeList>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(changes, reload) {
        if (list != null) delay(500) // several changes in a row (a craft, a transfer): ask once
        list = crafting.recipes()
    }
    var canMakeOnly by rememberSaveable { mutableStateOf(true) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        if (message != null) {
            delay(4_000)
            message = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (val result = list) {
            null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Loading the game's recipes...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is Fetched.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(result.reason, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { reload++ }) { Text("Retry") }
            }
            is Fetched.Ready -> {
                val recipes = result.value.recipes
                val shown = recipes.filter { (!canMakeOnly || it.canCraft) && (category == null || it.category == category) }
                Column(verticalArrangement = Arrangement.spacedBy(PaneSpacing)) {
                    Filters(result.value, canMakeOnly, { canMakeOnly = it }, category, { category = it })
                    Text(
                        if (canMakeOnly) "You can make ${shown.size} of ${recipes.count { category == null || it.category == category }}"
                        else "${shown.size} recipes",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    message?.let { Text(it, color = ErrorText, style = MaterialTheme.typography.bodySmall) }
                    if (shown.isEmpty()) {
                        Text(
                            if (canMakeOnly) "Nothing you can make with what's in reach. Turn off \"Can make\" to see the rest."
                            else "No recipes here.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 64.dp),
                        horizontalArrangement = Arrangement.spacedBy(Gap),
                        verticalArrangement = Arrangement.spacedBy(Gap),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(shown, key = { it.id }) { recipe ->
                            RecipeTile(recipe, iconUrl, selected = recipe.id == selectedId, onClick = { selectedId = recipe.id })
                        }
                    }
                }
            }
        }
        selectedId?.let { id ->
            RecipePanel(
                crafting, id, iconUrl, changes,
                onCrafted = { failure ->
                    if (failure == null) selectedId = null else message = failure
                },
                onClose = { selectedId = null },
            )
        }
    }
}

/** "Can make" and the categories, like the game window's filter and category list. */
@Composable
private fun Filters(
    list: RecipeList,
    canMakeOnly: Boolean,
    onCanMakeOnly: (Boolean) -> Unit,
    category: String?,
    onCategory: (String?) -> Unit,
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
            selected = canMakeOnly, onClick = { onCanMakeOnly(!canMakeOnly) }, label = { Text("Can make") },
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Good.copy(alpha = 0.25f)),
        )
        FilterChip(selected = category == null, onClick = { onCategory(null) }, label = { Text("All") })
        list.categories.sortedBy { it.name }.forEach { c ->
            FilterChip(selected = category == c.id, onClick = { onCategory(if (category == c.id) null else c.id) }, label = { Text(c.name) })
        }
    }
}

/** A recipe like an inventory tile; dimmed when something is missing. */
@Composable
private fun RecipeTile(recipe: RecipeSummary, iconUrl: (String) -> String, selected: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, border = selectedBorder(selected), modifier = Modifier.height(TileHeight)) {
        Column(
            Modifier.fillMaxSize().padding(3.dp).alpha(if (recipe.canCraft) 1f else 0.4f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(38.dp), contentAlignment = Alignment.Center) {
                GameIcon(recipe.icon, iconUrl, 36.dp)
            }
            Text(recipe.name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

/** What a recipe needs and makes, and Craft (several at once with − / +). */
@Composable
private fun BoxScope.RecipePanel(
    crafting: Crafting,
    id: String,
    iconUrl: (String) -> String,
    changes: Any?,
    onCrafted: (failure: String?) -> Unit,
    onClose: () -> Unit,
) = BottomPanel(onDismiss = onClose) {
    // Fetched again when what's in reach changes, so the ✓ / ✗ stay true.
    val details by produceState<Fetched<RecipeDetails>?>(null, id, changes) { value = crafting.recipe(id) }
    val scope = rememberCoroutineScope()
    when (val result = details) {
        null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text("Loading the recipe...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is Fetched.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(result.reason, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onClose) { Text("Close") }
        }
        is Fetched.Ready -> {
            val recipe = result.value
            var count by remember(recipe.id) { mutableIntStateOf(1) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GameIcon(recipe.icon, iconUrl, 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(recipe.name, style = MaterialTheme.typography.titleMedium)
                    val details = listOfNotNull(
                        recipe.category,
                        recipe.seconds?.let { if (it >= 60) "about ${it / 60} min" else "about $it s" },
                    ) + recipe.skills.map { "${it.name} ${it.level}" }
                    Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onClose) { Text("Close") }
            }
            HorizontalDivider()
            Section("Ingredients")
            recipe.inputs.forEach { IngredientRow(it, iconUrl) }
            recipe.skills.filter { !it.ok }.forEach {
                Text("Needs ${it.name} ${it.level} (you have ${it.have})", color = Danger, style = MaterialTheme.typography.bodySmall)
            }
            if (recipe.outputs.isNotEmpty()) {
                Section("Makes")
                recipe.outputs.forEach { output ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GameIcon(output.icon, iconUrl, 26.dp)
                        Text(output.name + amountSuffix(output.amount, output.unit), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            val missing = recipe.inputs.filter { !it.ok }.map { it.name } + recipe.skills.filter { !it.ok }.map { it.name }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (recipe.canCraft && recipe.max > 1) {
                    OutlinedButton(onClick = { count = (count - 1).coerceAtLeast(1) }, enabled = count > 1) { Text("−") }
                    Text("$count", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { count = (count + 1).coerceAtMost(recipe.max) }, enabled = count < recipe.max) { Text("+") }
                }
                Button(
                    enabled = recipe.canCraft && recipe.max > 0,
                    onClick = {
                        scope.launch {
                            val craft = crafting.craft(recipe.id, count)
                            onCrafted((craft as? CommandResult.Failed)?.let { "${recipe.name}: ${it.reason}" })
                        }
                    },
                ) { Text(if (count > 1) "Craft $count" else "Craft") }
                when {
                    missing.isNotEmpty() -> Text("Missing: " + missing.joinToString(", "), color = Danger,
                        style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    recipe.max > 1 -> Text("up to ${recipe.max}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** An ingredient: what (the item you have for it, or one that would do), have / need, ✓ or ✗. */
@Composable
private fun IngredientRow(input: RecipeInput, iconUrl: (String) -> String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GameIcon(input.icon, iconUrl, 26.dp)
        Column(Modifier.weight(1f)) {
            Text(input.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val notes = listOfNotNull(
                "kept".takeIf { input.keep },
                "or ${input.others} other${if (input.others > 1) "s" else ""}".takeIf { input.others > 0 },
            )
            if (notes.isNotEmpty()) {
                Text(notes.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "${number(input.have)}/${number(input.need)}${input.unit ?: ""} " + if (input.ok) "✓" else "✗",
            color = if (input.ok) Good else Danger,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun GameIcon(icon: String?, iconUrl: (String) -> String, size: Dp) {
    Box(Modifier.size(size)) {
        icon?.let {
            AsyncImage(model = iconUrl(it), contentDescription = null, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize())
        }
    }
}

private fun number(value: Float) = if (value == value.toInt().toFloat()) value.toInt().toString() else "%.1f".format(value)

private fun amountSuffix(amount: Float, unit: String?) = when {
    unit != null -> " ${number(amount)}$unit"
    amount > 1f -> " ×${number(amount)}"
    else -> ""
}
