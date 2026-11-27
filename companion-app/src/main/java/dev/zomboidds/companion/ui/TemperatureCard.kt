package dev.zomboidds.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.zomboidds.companion.domain.Temperature
import dev.zomboidds.companion.domain.TemperatureState

/** Cold blue, normal green, hot orange: the colour of the temperature everywhere on Status. */
@Composable
internal fun Temperature.color(): Color = when (state) {
    TemperatureState.COLD -> Cold
    TemperatureState.HOT -> Warning
    TemperatureState.NORMAL -> Good
}

private val Cold = Color(0xFF4FA3E0)

internal val TemperatureState.label: String
    get() = when (this) {
        TemperatureState.COLD -> "Cold"
        TemperatureState.HOT -> "Hot"
        TemperatureState.NORMAL -> "Normal"
    }

/** Worth more than one line: not a normal temperature, or noticeably wet. */
private val Temperature.notable: Boolean
    get() = state != TemperatureState.NORMAL || (bodyWet ?: 0f) > 0.25f || (clothesWet ?: 0f) > 0.25f

/**
 * Status, top of the right column: the core temperature, one line while all is fine. When cold, hot
 * or wet, also the game's heat bar and insulation, wind resistance and wetness. Tap for the panel.
 */
@Composable
internal fun TemperatureCard(temperature: Temperature, iconUrl: (String) -> String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(temperature.format(temperature.core), style = MaterialTheme.typography.titleMedium)
            Text(temperature.state.label, Modifier.weight(1f), color = temperature.color())
            Text("›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
        }
        if (temperature.notable) {
            HeatBar(temperature.coreBar, iconUrl)
            BarLabels("Cold", "Normal", "Hot")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                temperature.insulation?.let { Fact("Insulation", "%.2f".format(it)) }
                temperature.wind?.let { Fact("Wind resistance", "%.2f".format(it)) }
                temperature.bodyWet?.let { Fact("Wetness", percent(it)) }
                temperature.clothesWet?.let { Fact("Clothes", percent(it)) }
            }
        }
    }
}

private fun percent(value: Float) = "${(value * 100).toInt()}%"

@Composable
private fun Fact(label: String, value: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("$label ") }
            append(value)
        },
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
        style = MaterialTheme.typography.bodySmall,
    )
}

/** The game's heat bar texture with a marker at [value] (0-1), as its Temperature tab draws it. */
@Composable
internal fun HeatBar(value: Float, iconUrl: (String) -> String, texture: String = "heatbar_horz") {
    BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp), contentAlignment = Alignment.CenterStart) {
        AsyncImage(iconUrl(texture), null, Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.FillBounds)
        val x = (maxWidth - 4.dp) * value.coerceIn(0f, 1f)
        Box(Modifier.offset(x = x).width(4.dp).height(14.dp).background(Color.Black, RoundedCornerShape(2.dp)).padding(1.dp)
            .background(Color.White, RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun BarLabels(vararg labels: String) {
    Row(Modifier.fillMaxWidth().padding(top = 0.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/**
 * The game's Temperature tab in a panel: the body coloured by skin temperature, the core temperature,
 * body response (fighting cold or heat), insulation, wind resistance and wetness on its bars, and
 * the coldest (or warmest) parts.
 */
@Composable
internal fun BoxScope.TemperaturePanel(
    temperature: Temperature,
    female: Boolean,
    iconUrl: (String) -> String,
    onClose: () -> Unit,
) = BottomPanel(onDismiss = onClose) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Temperature", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onClose) { Text("Close") }
    }
    HorizontalDivider()
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val skin = temperature.parts.associate { it.id to it.skinBar }
        BodySilhouette(female, iconUrl, Modifier.width(110.dp).height(270.dp)) { id ->
            skin[id]?.let(::heatColor) ?: Color(0xFF4F4943)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Labelled("Core temperature · ${temperature.format(temperature.core)}") { HeatBar(temperature.coreBar, iconUrl) }
            temperature.response?.let { response ->
                val word = when {
                    response < 0.45f -> "Fighting Cold"
                    response > 0.55f -> "Fighting Heat"
                    else -> "Normal"
                }
                Labelled("Body response · $word") { HeatBar(response, iconUrl) }
            }
            temperature.insulationBar?.let { bar ->
                Labelled("Insulation · ${"%.2f".format(temperature.insulation ?: 0f)}") { HeatBar(bar, iconUrl) }
            }
            temperature.windBar?.let { bar ->
                Labelled("Wind resistance · ${"%.2f".format(temperature.wind ?: 0f)}") { HeatBar(bar, iconUrl) }
            }
            temperature.bodyWet?.let { Labelled("Body wetness · ${percent(it)}") { HeatBar(it, iconUrl, "heatbar_horz_blues") } }
            temperature.clothesWet?.let { Labelled("Clothing wetness · ${percent(it)}") { HeatBar(it, iconUrl, "heatbar_horz_blues") } }
            val hot = temperature.state == TemperatureState.HOT
            val parts = temperature.parts.filter { it.skin != null }
                .sortedBy { if (hot) -it.skin!! else it.skin!! }.take(6)
            if (parts.isNotEmpty()) {
                Text(if (hot) "Warmest" else "Coldest", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                parts.chunked(2).forEach { pair ->
                    Row {
                        pair.forEach { part ->
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(10.dp).background(heatColor(part.skinBar ?: 0.5f), RoundedCornerShape(3.dp)))
                                Text(part.name, style = MaterialTheme.typography.bodySmall)
                                Text(temperature.format(part.skin!!), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (pair.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Labelled(label: String, bar: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        bar()
    }
}

/** The game's colour scheme for skin temperature (ISClothingInsPanel): blue, cyan, green, yellow, red. */
internal fun heatColor(value: Float): Color {
    val stops = listOf(
        0f to Color(29, 34, 237), 0.25f to Color(0, 255, 234), 0.5f to Color(84, 255, 55),
        0.75f to Color(255, 246, 0), 1f to Color(255, 0, 0),
    )
    val v = value.coerceIn(0f, 1f)
    for (i in 1 until stops.size) {
        val (b, cb) = stops[i]
        if (v <= b) {
            val (a, ca) = stops[i - 1]
            return lerp(ca, cb, (v - a) / (b - a))
        }
    }
    return stops.last().second
}
