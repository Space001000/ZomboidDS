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


/** Dashboard for the vehicle the player is in. */
@Composable
fun VehicleScreen(vehicle: Vehicle.Driving) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(vehicle.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(if (vehicle.isDriver) "Driver" else "Passenger", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("%.0f".format(vehicle.speedKmh), fontSize = 96.sp, fontWeight = FontWeight.Bold)
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

@Preview(widthDp = 540)
@Composable
private fun DrivingPreview() {
    VehicleScreen(Vehicle.Driving("Chevalier Nyala", speedKmh = 57f, engineRunning = true, fuel = 0.12f, isDriver = true))
}
