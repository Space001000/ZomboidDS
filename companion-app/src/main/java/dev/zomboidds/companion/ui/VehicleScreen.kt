package dev.zomboidds.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zomboidds.companion.domain.Vehicle
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.GameControls
import dev.zomboidds.companion.domain.HereState
import dev.zomboidds.companion.domain.ItemActions
import dev.zomboidds.companion.domain.MenuOption
import kotlinx.coroutines.launch


/**
 * Dashboard for the vehicle the player is in. [compact]: smaller, leaving room for Here below it
 * (the dashboard rework is still to come).
 */
@Composable
fun VehicleScreen(vehicle: Vehicle.Driving, compact: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(vehicle.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(if (vehicle.isDriver) "Driver" else "Passenger", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(if (compact) 10.dp else 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("%.0f".format(vehicle.speedKmh), fontSize = if (compact) 48.sp else 96.sp, fontWeight = FontWeight.Bold)
                Text("km/h", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row {
                    Text("Engine", Modifier.weight(1f))
                    Text(
                        if (vehicle.engineRunning) "Running" else "Off",
                        color = if (vehicle.engineRunning) Good else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                val fuel = vehicle.fuel
                Row {
                    Text("Fuel", Modifier.weight(1f))
                    Text(fuel?.let { "${(it * 100).toInt()}%" } ?: "unknown",
                        color = if (fuel != null && fuel < 0.15f) Danger else MaterialTheme.colorScheme.onSurface)
                }
                if (fuel != null) {
                    LinearProgressIndicator(
                        progress = { fuel.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(10.dp),
                        color = if (fuel < 0.15f) Danger else MaterialTheme.colorScheme.primary,
                        drawStopIndicator = {},
                        gapSize = 0.dp,
                    )
                }
            }
        }
    }
}

/**
 * The Here tab while driving (titled Vehicle): the dashboard as one row of tiles, then the game's
 * vehicle menu (what the controller's radial menu offers: engine, lights, heater, horn, windows,
 * doors, sleep, seats, exit) as big buttons with the game's own icons. A greyed button's label is
 * the game's reason; tapping it shows all of it.
 */
@Composable
fun VehicleTab(
    vehicle: Vehicle.Driving,
    here: HereState?,
    controls: GameControls,
    actions: ItemActions,
    iconUrl: (String) -> String,
) {
    WatchHere(controls)
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // All three as tall as the fuel tile (its bar makes it the tallest).
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Gauge("%.0f".format(vehicle.speedKmh), "km/h", Modifier.weight(1f).fillMaxHeight())
            Gauge(if (vehicle.engineRunning) "On" else "Off", "engine", Modifier.weight(1f).fillMaxHeight(),
                color = if (vehicle.engineRunning) Good else MaterialTheme.colorScheme.onSurface)
            val fuel = vehicle.fuel
            Gauge(fuel?.let { "${(it * 100).toInt()}%" } ?: "?", "fuel", Modifier.weight(1f).fillMaxHeight(),
                color = if (fuel != null && fuel < 0.15f) Danger else MaterialTheme.colorScheme.onSurface, bar = fuel)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(vehicle.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(if (vehicle.isDriver) "Driver" else "Passenger", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        val menu = here?.menu
        when {
            menu != null -> {
                // The vehicle menu comes as one entry named after the car; its options are the buttons.
                val options = menu.options.flatMap { if (it.children.isEmpty()) listOf(it) else it.children }
                ActionGrid(options, iconUrl) { option ->
                    if (!option.enabled) {
                        message = option.tooltip ?: option.name
                    } else {
                        message = null
                        scope.launch {
                            val result = actions.selectMenuOption(menu.menuId, option.id)
                            if (result is CommandResult.Failed) message = result.reason
                        }
                    }
                }
            }
            here?.unavailable != null -> Text(here.unavailable, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Text("Looking around...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Gauge(value: String, unit: String, modifier: Modifier, color: Color = MaterialTheme.colorScheme.onSurface, bar: Float? = null) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = color)
            Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (bar != null) {
                LinearProgressIndicator(
                    progress = { bar.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp),
                    color = if (bar < 0.15f) Danger else MaterialTheme.colorScheme.primary,
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                )
            }
        }
    }
}

private val ActionTileHeight = 104.dp
private val ActionTileMinWidth = 110.dp

@Composable
private fun ActionGrid(options: List<MenuOption>, iconUrl: (String) -> String, onTap: (MenuOption) -> Unit) {
    BoxWithConstraints {
        val columns = ((maxWidth + 8.dp) / (ActionTileMinWidth + 8.dp)).toInt().coerceIn(2, 6)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { option ->
                        Surface(
                            onClick = { onTap(option) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.weight(1f).height(ActionTileHeight),
                        ) {
                            Column(
                                Modifier.fillMaxSize().padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                            ) {
                                option.icon?.let {
                                    AsyncImage(model = iconUrl(it), contentDescription = null, filterQuality = FilterQuality.None,
                                        alpha = if (option.enabled) 1f else 0.35f, modifier = Modifier.size(32.dp))
                                }
                                Text(
                                    option.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    color = if (option.enabled) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                )
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Preview(widthDp = 540)
@Composable
private fun DrivingPreview() {
    VehicleScreen(Vehicle.Driving("Chevalier Nyala", speedKmh = 57f, engineRunning = true, fuel = 0.12f, isDriver = true))
}
