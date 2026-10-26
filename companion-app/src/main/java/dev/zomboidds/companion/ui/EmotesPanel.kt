package dev.zomboidds.companion.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.zomboidds.companion.AppSettings
import dev.zomboidds.companion.domain.Emote
import dev.zomboidds.companion.domain.EmoteGroup
import kotlinx.coroutines.delay

/**
 * The player's favourite emotes (ids, at most [AppSettings.MAX_FAVOURITE_EMOTES]) and the Emotes group
 * open last, and how to change them.
 */
class EmoteSettings(
    val favourites: List<String> = emptyList(),
    val onFavouritesChange: (List<String>) -> Unit = {},
    val lastGroup: String? = null,
    val onLastGroupChange: (String) -> Unit = {},
)


/**
 * The game's emote wheel, one group at a time: its groups as chips, Favourites last (the panel opens
 * on the group used last, so its place matters only the first time). Tap an emote to play it; hold
 * one to add it to Favourites or take it out.
 */
@Composable
internal fun EmotesPanel(groups: List<EmoteGroup>, settings: EmoteSettings, iconUrl: (String) -> String, onPlay: (String) -> Unit) {
    var group by remember { mutableStateOf(settings.lastGroup?.takeIf { id -> id == AppSettings.FAVOURITES || groups.any { it.id == id } }
        ?: groups.firstOrNull()?.id ?: AppSettings.FAVOURITES) }
    val favourites = settings.favourites
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        if (message != null) {
            delay(3_000)
            message = null
        }
    }
    val byId = remember(groups) { groups.flatMap { it.emotes }.associateBy { it.id } }
    fun choose(id: String) {
        group = id
        settings.onLastGroupChange(id)
    }
    fun toggle(emote: Emote) {
        val next = when {
            emote.id in favourites -> favourites - emote.id
            favourites.size >= AppSettings.MAX_FAVOURITE_EMOTES -> {
                message = "Favourites hold ${AppSettings.MAX_FAVOURITE_EMOTES}: hold one there to take it out first."
                return
            }
            else -> favourites + emote.id
        }
        settings.onFavouritesChange(next)
    }

    BoxWithConstraints {
    val columns = deckColumns(maxWidth)
    // One height for every group, so the panel holds still while switching: the largest group's
    // rows (Favourites counted full), or as many as fit, and then the grid scrolls.
    fun rows(count: Int) = (count + columns - 1) / columns
    val rows = (groups.map { rows(it.emotes.size) } + rows(AppSettings.MAX_FAVOURITE_EMOTES)).max().coerceAtLeast(1)
    val needed = DeckTileHeight * rows + EmoteGap * (rows - 1)
    val gridHeight = minOf(needed, (maxHeight - EmoteHeaderHeight - EmoteChipHeight - 20.dp).coerceAtLeast(DeckTileHeight))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().height(EmoteHeaderHeight), verticalAlignment = Alignment.CenterVertically) {
            Text("Emotes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 12.dp))
            // In the header, so it doesn't change the panel's height either.
            message?.let {
                Text(it, Modifier.weight(1f), color = ErrorText, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        // The chips share the row, icon above the name, so all of them (Favourites last) fit on a narrow screen.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            groups.forEach { g -> GroupChip(g.name, g.icon, selected = g.id == group, iconUrl, Modifier.weight(1f)) { choose(g.id) } }
            GroupChip("Favourites", "FavoriteStar", selected = group == AppSettings.FAVOURITES, iconUrl, Modifier.weight(1f)) { choose(AppSettings.FAVOURITES) }
        }
        // Favourites in their own order; ids this game doesn't have are left out.
        val shown = if (group == AppSettings.FAVOURITES) favourites.mapNotNull { byId[it] }
            else groups.firstOrNull { it.id == group }?.emotes.orEmpty()
        Box(Modifier.fillMaxWidth().height(gridHeight)) {
            if (group == AppSettings.FAVOURITES && shown.isEmpty()) {
                Text("Hold an emote in any group to add it here (up to ${AppSettings.MAX_FAVOURITE_EMOTES}).",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
            }
            Column(Modifier.verticalScroll(rememberScrollState(), enabled = needed > gridHeight),
                verticalArrangement = Arrangement.spacedBy(EmoteGap)) {
                shown.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(EmoteGap)) {
                        row.forEach { emote ->
                            EmoteTile(emote, favourite = emote.id in favourites, iconUrl, onPlay = { onPlay(emote.id) },
                                onHold = { toggle(emote) }, modifier = Modifier.weight(1f))
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
    }
}

private val EmoteGap = 8.dp
private val EmoteHeaderHeight = 28.dp
private val EmoteChipHeight = 52.dp

@Composable
private fun GroupChip(name: String, icon: String?, selected: Boolean, iconUrl: (String) -> String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        modifier = modifier.height(EmoteChipHeight),
    ) {
        Column(Modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
            icon?.let { AsyncImage(iconUrl(it), null, filterQuality = FilterQuality.None, modifier = Modifier.size(22.dp)) }
            Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One emote; a favourite carries the game's favourite star in its corner. */
@Composable
private fun EmoteTile(emote: Emote, favourite: Boolean, iconUrl: (String) -> String, onPlay: () -> Unit, onHold: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.height(DeckTileHeight).clip(shape).combinedClickable(role = Role.Button, onLongClick = onHold, onClick = onPlay),
    ) {
        Box {
            Column(
                Modifier.fillMaxSize().padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                emote.icon?.let { AsyncImage(iconUrl(it), null, filterQuality = FilterQuality.None, modifier = Modifier.size(40.dp)) }
                Text(emote.name, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            if (favourite) {
                AsyncImage(iconUrl("FavoriteStar"), "Favourite", filterQuality = FilterQuality.None,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(14.dp))
            }
        }
    }
}
