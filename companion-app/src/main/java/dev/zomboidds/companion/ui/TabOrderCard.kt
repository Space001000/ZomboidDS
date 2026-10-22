package dev.zomboidds.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

private val TabRowHeight = 46.dp
private val TabRowGap = 6.dp

/** A tab as the order card lists it: [icon] is a game image for tabs drawn as one (Map), [hint] says what else it shows. */
internal class TabChoice(val id: String, val title: String, val icon: String? = null, val hint: String? = null)

/**
 * The tab order, opened by holding a tab: one row per tab, dragged by its handle like the Deck
 * editor's. Changes apply at once, so the tab bar above the dim follows along. Sits just below
 * the tab bar to keep it in view; tapping the dimmed part closes it, like Done.
 */
@Composable
internal fun BoxScope.TabOrderCard(
    tabs: List<TabChoice>,
    iconUrl: (String) -> String,
    onMove: (from: Int, to: Int) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = null, indication = null, onClick = onClose),
    )
    Surface(
        Modifier.align(Alignment.TopCenter).padding(top = 62.dp, bottom = 8.dp).padding(horizontal = 16.dp)
            .widthIn(max = 380.dp).fillMaxWidth()
            // Taps on the card itself don't reach the dim (which would close it).
            .clickable(interactionSource = null, indication = null, onClick = {}),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tab order", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onReset) { Text("Reset") }
                Button(onClick = onClose, modifier = Modifier.padding(start = 8.dp)) { Text("Done") }
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(TabRowGap),
            ) {
                // A column for icons only when a tab has one (Map on its own tab).
                val icons = tabs.any { it.icon != null }
                DragRows(tabs, key = { it.id }, rowHeight = TabRowHeight, gap = TabRowGap, onMove = onMove) { tab, handle, lifted ->
                    TabOrderRow(tab, iconUrl, icons, handle, lifted)
                }
            }
        }
    }
}

@Composable
private fun TabOrderRow(tab: TabChoice, iconUrl: (String) -> String, icons: Boolean, handle: Modifier, lifted: Boolean) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (lifted) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = if (lifted) 6.dp else 0.dp,
        modifier = Modifier.fillMaxWidth().height(TabRowHeight),
    ) {
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(handle.width(28.dp).height(TabRowHeight), contentAlignment = Alignment.Center) {
                Text("⋮⋮", color = MaterialTheme.colorScheme.outline)
            }
            if (icons) {
                Box(Modifier.padding(end = 10.dp).size(30.dp), contentAlignment = Alignment.Center) {
                    tab.icon?.let {
                        AsyncImage(iconUrl(it), null, filterQuality = FilterQuality.None, modifier = Modifier.size(28.dp))
                    }
                }
            }
            Text(tab.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 4.dp).weight(1f))
            tab.hint?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
