package dev.zomboidds.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The search box in place of the filter row while the search keyboard is up: the words typed, and
 * Close (clears the search).
 */
@Composable
internal fun SearchBar(query: String, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.weight(1f).height(40.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text("Search", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(query + "▏", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        TextButton(onClick = onClose) { Text("Close") }
    }
}

/**
 * A small keyboard for Craft and Build search, docked at the bottom like the radio's frequency pad.
 * The app's window can't take focus while the game runs (the controller stays with the game), so
 * Android's keyboard can't come up: this one types without focus. The list above filters as you
 * type; [SearchBar] shows the words. Done keeps the search.
 */
@Composable
internal fun BoxScope.SearchKeyboard(query: String, onQuery: (String) -> Unit, onDone: () -> Unit) {
    Surface(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().clickable(interactionSource = null, indication = null, onClick = {}),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            ROWS.forEachIndexed { index, row ->
                // The middle rows sit in a little, like a real keyboard.
                Row(Modifier.fillMaxWidth().padding(horizontal = (index * 12).dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    row.forEach { letter -> Key(letter.toString()) { onQuery(query + letter.lowercaseChar()) } }
                    if (index == ROWS.lastIndex) Key("⌫", weight = 1.5f, accent = true) { onQuery(query.dropLast(1)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Key("Clear", weight = 1.5f, accent = true) { onQuery("") }
                Key("Space", weight = 5f) { if (query.isNotEmpty() && !query.endsWith(" ")) onQuery("$query ") }
                Key("Done", weight = 1.5f, accent = true, onClick = onDone)
            }
        }
    }
}

/** How much of the screen the keyboard takes, so the list can scroll out from under it. */
internal val SearchKeyboardHeight = 180.dp

private val ROWS = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")

@Composable
private fun RowScope.Key(label: String, weight: Float = 1f, accent: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.weight(weight).height(36.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = if (label.length == 1) 18.sp else 14.sp,
            color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

/** Whether [name] matches a search: every word of it, in any order, ignoring case. */
internal fun matchesSearch(name: String, query: String): Boolean {
    val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return words.all { name.contains(it, ignoreCase = true) }
}
