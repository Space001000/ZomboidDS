package dev.zomboidds.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.zomboidds.companion.domain.ItemMenu
import dev.zomboidds.companion.domain.ItemMenuResult
import dev.zomboidds.companion.domain.MenuOption

/** The game's right-click menu: submenus open in place, greyed options show the game's reason. */
@Composable
internal fun GameMenu(menu: ItemMenu, onSelect: (optionId: String) -> Unit, top: List<MenuOption> = menu.options) {
    var path by remember(menu.menuId) { mutableStateOf(listOf<MenuOption>()) }
    val options = path.lastOrNull()?.children ?: top
    Column {
        if (path.isNotEmpty()) {
            TextButton(onClick = { path = path.dropLast(1) }) { Text("‹ " + path.joinToString(" › ") { it.name }) }
        }
        options.forEach { option ->
            val submenu = option.children.isNotEmpty()
            Row(
                Modifier.fillMaxWidth()
                    .clickable(enabled = option.enabled) { if (submenu) path = path + option else onSelect(option.id) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        option.name,
                        color = if (option.enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                    option.tooltip?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (submenu) Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * The game's menu for something (an item, a body part), fetched when shown: a spinner while it
 * loads, the game's reason with Retry when there's none, then the menu. While [enabled] is false
 * nothing is fetched and [whileDisabled] shows instead (e.g. "Unpause to treat"); it loads by itself
 * once enabled. [content] draws the menu (the whole menu as a list by default); [untilReady]
 * goes above the spinner or the reason, until there's a menu to show.
 */
@Composable
internal fun LoadingGameMenu(
    key: Any,
    load: suspend () -> ItemMenuResult,
    onSelect: (menuId: String, optionId: String) -> Unit,
    enabled: Boolean = true,
    whileDisabled: @Composable () -> Unit = {},
    untilReady: @Composable () -> Unit = {},
    content: @Composable (menu: ItemMenu, onSelect: (optionId: String) -> Unit) -> Unit = { menu, select -> GameMenu(menu, select) },
) {
    var reload by remember { mutableIntStateOf(0) }
    val menu by produceState<ItemMenuResult?>(null, key, reload, enabled) {
        value = null
        if (enabled) value = load()
    }
    when (val result = menu) {
        null if !enabled -> whileDisabled()
        null -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            untilReady()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Loading the game's menu...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        is ItemMenuResult.Failed -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            untilReady()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(result.reason, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { reload++ }) { Text("Retry") }
            }
        }
        is ItemMenuResult.Ready -> content(result.menu) { optionId -> onSelect(result.menu.menuId, optionId) }
    }
}

/**
 * A panel over the bottom of the screen with the rest dimmed; tapping the dimmed part closes it.
 * Drawn in this window, not as a dialog: a new window could take focus from the game.
 */
@Composable
internal fun BoxScope.BottomPanel(onDismiss: () -> Unit, spacing: Dp = 8.dp, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = null, indication = null, onClick = onDismiss),
    )
    Card(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        Column(
            Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}
