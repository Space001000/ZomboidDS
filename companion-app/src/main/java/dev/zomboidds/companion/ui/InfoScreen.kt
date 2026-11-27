package dev.zomboidds.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.zomboidds.companion.domain.CharacterInfo
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.GameControls
import dev.zomboidds.companion.domain.GameSpeed
import dev.zomboidds.companion.domain.ItemActions
import dev.zomboidds.companion.domain.StyleKind
import dev.zomboidds.companion.domain.TimeState
import dev.zomboidds.companion.domain.Trait
import dev.zomboidds.companion.domain.TraitTone
import dev.zomboidds.companion.domain.WeightTrend
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Status ▾ Info: the game's Info tab without its 3D figure. On the left who the character is (the
 * profession's icon, name, profession, kills, time survived); on the right weight, traits (tap one
 * for the game's description), hair and beard with the game's haircut menu, and favourite weapon.
 */
@Composable
fun InfoScreen(
    info: CharacterInfo,
    controls: GameControls,
    actions: ItemActions,
    iconUrl: (String) -> String,
    time: TimeState?,
    onUnpause: () -> Unit,
) {
    var styling by remember { mutableStateOf<StyleKind?>(null) }
    var openTrait by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(failure) {
        if (failure != null) {
            delay(4_000)
            failure = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WhoCard(info, iconUrl)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                failure?.let { Text(it, color = ErrorText, modifier = Modifier.padding(bottom = 6.dp)) }
                info.weight?.let { weight ->
                    InfoRow("Weight") {
                        Text("$weight")
                        info.weightTrend?.let { trend ->
                            AsyncImage(iconUrl(trend.icon), null, Modifier.padding(start = 6.dp).size(13.dp, if (trend == WeightTrend.UP_LOT) 13.dp else 8.dp))
                        }
                    }
                }
                if (info.traits.isNotEmpty()) {
                    Traits(info.traits, iconUrl, openTrait) { id -> openTrait = if (openTrait == id) null else id }
                }
                info.hair?.let { hair ->
                    InfoRow("Hair") {
                        Text(hair, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        ChangeButton(info.canChangeHair) { styling = StyleKind.HAIR }
                    }
                }
                info.beard?.let { beard ->
                    InfoRow("Beard") {
                        Text(beard, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        ChangeButton(info.canChangeBeard) { styling = StyleKind.BEARD }
                    }
                }
                info.favouriteWeapon?.let { InfoRow("Favorite Weapon") { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
            }
        }
        styling?.let { kind ->
            StylePanel(kind, if (kind == StyleKind.HAIR) info.hair else info.beard, time, onUnpause,
                load = { controls.styleMenu(kind) },
                onSelect = { menuId, optionId ->
                    styling = null
                    scope.launch {
                        val result = actions.selectMenuOption(menuId, optionId)
                        if (result is CommandResult.Failed) failure = result.reason
                    }
                },
                onClose = { styling = null })
        }
    }
}

/** Left: the profession's icon, name and profession; kills and time survived at the bottom. */
@Composable
private fun WhoCard(info: CharacterInfo, iconUrl: (String) -> String) {
    Column(
        Modifier.width(160.dp).fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        info.professionIcon?.let { AsyncImage(iconUrl(it), info.profession, Modifier.size(64.dp)) }
        Text(info.name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        info.profession?.let { Text(it, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center) }
        Box(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            info.kills?.let { kills ->
                Row {
                    Text("Zombies Killed", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("%,d".format(kills), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
            info.survived?.let {
                Text("Survived For", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** A label and its value, with a line under it like the mockup's table. */
@Composable
private fun InfoRow(label: String, value: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.width(140.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, content = value)
        }
        HorizontalDivider()
    }
}

/** The traits in two columns, each its icon and name; the tapped one's description below them. */
@Composable
private fun Traits(traits: List<Trait>, iconUrl: (String) -> String, open: String?, onTap: (String) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Traits", color = MaterialTheme.colorScheme.onSurfaceVariant)
        traits.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { trait -> TraitChip(trait, iconUrl, trait.id == open, Modifier.weight(1f)) { onTap(trait.id) } }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
        traits.firstOrNull { it.id == open }?.let { trait ->
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(trait.name) }
                    trait.description?.takeIf { it.isNotBlank() }?.let { append(": ${it.trim()}") }
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        HorizontalDivider(Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun TraitChip(trait: Trait, iconUrl: (String) -> String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier.heightIn(min = 36.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        trait.icon?.let { AsyncImage(iconUrl(it), null, Modifier.size(18.dp)) }
        Text(
            trait.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (trait.tone == TraitTone.BAD) BadTrait else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Traits that gave points, in the mockup's soft red: readable, not alarming. */
private val BadTrait = androidx.compose.ui.graphics.Color(0xFFE8A090)

/** The game's Change button: greyed without hair or beard to cut, like in the game. */
@Composable
private fun ChangeButton(enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled) { Text("Change ›") }
}

/** The game's chevron next to the weight: gaining, gaining a lot, losing. */
private val WeightTrend.icon: String
    get() = when (this) {
        WeightTrend.UP -> "chevron_up"
        WeightTrend.UP_LOT -> "chevron_double"
        WeightTrend.DOWN -> "chevron_down"
    }

/** The game's haircut or beard menu, like the treatment panel: the styles, greyed with its reason. */
@Composable
private fun BoxScope.StylePanel(
    kind: StyleKind,
    current: String?,
    time: TimeState?,
    onUnpause: () -> Unit,
    load: suspend () -> dev.zomboidds.companion.domain.ItemMenuResult,
    onSelect: (menuId: String, optionId: String) -> Unit,
    onClose: () -> Unit,
) = BottomPanel(onDismiss = onClose) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(if (kind == StyleKind.HAIR) "Hair" else "Beard") }
                current?.let {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(" · ") }
                    append(it)
                }
            },
            Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onClose) { Text("Close") }
    }
    HorizontalDivider()
    // Like the treatment menu: the game acts on it only while it runs.
    LoadingGameMenu(
        key = kind,
        load = load,
        onSelect = onSelect,
        enabled = time?.speed != GameSpeed.PAUSED,
        whileDisabled = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (time?.gameMenuOpen == true) "Close the game's menu, then unpause" else "Unpause to change it",
                    Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (time?.canChange == true && !time.gameMenuOpen) TextButton(onClick = onUnpause) { Text("Unpause") }
            }
        },
    )
}

/** The tab is open but the game hasn't reported this yet. */
@Composable
internal fun Waiting() {
    Text("Waiting for the game...", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}
