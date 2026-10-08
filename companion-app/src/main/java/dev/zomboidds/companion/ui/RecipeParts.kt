package dev.zomboidds.companion.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.zomboidds.companion.domain.RecipeCategory
import dev.zomboidds.companion.domain.RecipeDetails
import dev.zomboidds.companion.domain.RecipeInput

// The parts the Craft and Build screens share: both show the game's recipe windows (crafting and
// building), which work the same way in the game.

/** "Can make" / "Can build" and the categories, like the game windows' filter and category list. */
@Composable
internal fun RecipeFilters(
    onlyLabel: String,
    only: Boolean,
    onOnly: (Boolean) -> Unit,
    categories: List<RecipeCategory>,
    category: String?,
    onCategory: (String?) -> Unit,
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
            selected = only, onClick = { onOnly(!only) }, label = { Text(onlyLabel) },
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Good.copy(alpha = 0.25f)),
        )
        FilterChip(selected = category == null, onClick = { onCategory(null) }, label = { Text("All") })
        categories.sortedBy { it.name }.forEach { c ->
            FilterChip(selected = category == c.id, onClick = { onCategory(if (category == c.id) null else c.id) }, label = { Text(c.name) })
        }
    }
}

/**
 * A recipe panel's first row: its icon and name, then its category, time and skills once [recipe]
 * is known, and Close.
 */
@Composable
internal fun RecipeHeader(icon: String?, name: String, recipe: RecipeDetails?, iconUrl: (String) -> String, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GameIcon(icon, iconUrl, 44.dp)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            if (recipe != null) {
                val facts = listOfNotNull(
                    recipe.category,
                    recipe.seconds?.let { if (it >= 60) "about ${it / 60} min" else "about $it s" },
                ) + recipe.skills.map { "${it.name} ${it.level}" }
                Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onClose) { Text("Close") }
    }
}

/** What a recipe needs under [title] (Ingredients, Materials), and the skills that fall short. */
@Composable
internal fun ColumnScope.RecipeInputs(title: String, recipe: RecipeDetails, iconUrl: (String) -> String) {
    RecipeSection(title)
    recipe.inputs.forEach { IngredientRow(it, iconUrl) }
    recipe.skills.filter { !it.ok }.forEach {
        Text("Needs ${it.name} ${it.level} (you have ${it.have})", color = Danger, style = MaterialTheme.typography.bodySmall)
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
internal fun RecipeSection(title: String) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One of the game's icons, pixel art kept sharp; an empty square of [size] when there's none. */
@Composable
internal fun GameIcon(icon: String?, iconUrl: (String) -> String, size: Dp) {
    Box(Modifier.size(size)) {
        icon?.let {
            AsyncImage(model = iconUrl(it), contentDescription = null, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize())
        }
    }
}

internal fun number(value: Float) = if (value == value.toInt().toFloat()) value.toInt().toString() else "%.1f".format(value)
