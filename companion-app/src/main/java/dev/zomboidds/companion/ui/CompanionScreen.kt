package dev.zomboidds.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.zomboidds.companion.domain.BridgeInfo
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.ItemCommand
import dev.zomboidds.companion.domain.ConnectionStatus
import dev.zomboidds.companion.domain.GameState
import dev.zomboidds.companion.domain.PlayerStatus
import dev.zomboidds.companion.domain.SessionInfo
import dev.zomboidds.companion.setup.SetupReport

private val Healthy = Color(0xFF7CB342)
private val Danger = Color(0xFFE53935)

@Composable
fun CompanionScreen(
    state: GameState,
    connection: ConnectionStatus,
    setup: SetupReport,
    setupActions: SetupActions,
    iconUrl: (String) -> String,
    perform: suspend (ItemCommand) -> CommandResult,
) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            val inGame = connection == ConnectionStatus.Connected && state.session?.inGame == true
            if (inGame) {
                InGame(state, iconUrl, perform)
            } else {
                // Outside a game is when setup matters: show what's left to do.
                Column(
                    Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StatusHeader(state, connection)
                    SetupChecklist(setup, gameStep(connection), setupActions)
                }
            }
        }
    }
}

private enum class Tab(val title: String) { INVENTORY("Inventory"), STATUS("Status") }

@Composable
private fun InGame(state: GameState, iconUrl: (String) -> String, perform: suspend (ItemCommand) -> CommandResult) {
    var tab by rememberSaveable { mutableStateOf(Tab.INVENTORY) }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab.ordinal) {
            Tab.entries.forEach {
                Tab(selected = tab == it, onClick = { tab = it }, text = { Text(it.title) })
            }
        }
        Box(Modifier.fillMaxSize().padding(16.dp)) {
            when (tab) {
                Tab.INVENTORY -> InventoryScreen(state.inventory, iconUrl, perform)
                Tab.STATUS -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatusHeader(state, ConnectionStatus.Connected)
                    state.player?.let { PlayerCard(it) }
                }
            }
        }
    }
}

private fun gameStep(connection: ConnectionStatus) =
    if (connection == ConnectionStatus.Connected) {
        "Load a save with ZomboidDS enabled. Each save has its own mod list: for a save made " +
            "before installing ZomboidDS, enable it under Load → select the save → Mods."
    } else {
        "Start Project Zomboid in Zomdroid. This screen switches to your character once you're in a game."
    }

@Composable
private fun StatusHeader(state: GameState, connection: ConnectionStatus) {
    val (title, detail) = when {
        connection is ConnectionStatus.Waiting -> "Waiting for the game" to "Not running yet, or the mod isn't active."
        connection is ConnectionStatus.Connecting -> "Connecting..." to ""
        state.session?.inGame == true -> "In game" to "Project Zomboid ${state.session.gameVersion ?: ""}"
        else -> "Connected" to "The game is at the main menu."
    }
    Column {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (detail.isNotEmpty()) {
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlayerCard(player: PlayerStatus) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Health", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (player.bleeding) {
                    Text("BLEEDING", color = Danger, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                }
                Text(player.health?.let { "%.0f".format(it) } ?: "?", style = MaterialTheme.typography.headlineSmall)
            }
            Meter(player.health?.div(100f), if ((player.health ?: 100f) < 50f) Danger else Healthy)
            Stat("Hunger", player.hunger)
            Stat("Thirst", player.thirst)
            Stat("Fatigue", player.fatigue)
            Stat("Endurance", player.endurance)
        }
    }
}

@Composable
private fun Stat(label: String, value: Float?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium)
        Meter(value, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
    }
}

@Composable
private fun Meter(fraction: Float?, color: Color, modifier: Modifier = Modifier.fillMaxWidth()) {
    LinearProgressIndicator(
        progress = { (fraction ?: 0f).coerceIn(0f, 1f) },
        modifier = modifier.height(8.dp),
        color = color,
        drawStopIndicator = {},
        gapSize = 0.dp,
    )
}

@Preview(widthDp = 540, heightDp = 620)
@Composable
private fun InGamePreview() {
    CompanionScreen(
        state = GameState(
            bridge = BridgeInfo(1, "0.1.0", "b42"),
            session = SessionInfo(inGame = true, gameVersion = "42.20"),
            player = PlayerStatus(health = 72f, bleeding = true, hunger = 0.3f, thirst = 0.5f, fatigue = 0.1f, endurance = 0.9f),
        ),
        connection = ConnectionStatus.Connected,
        setup = SetupReport(zomdroidInstalled = true, hasAccess = true, bundledModVersion = "0.1.0"),
        setupActions = object : SetupActions {
            override fun grantAccess() {}
            override fun selectInstance(name: String) {}
            override fun getZombieBuddy() {}
            override fun openZomdroid() {}
            override fun installMod() {}
            override fun enableForNewGames() {}
        },
        iconUrl = { it },
        perform = { CommandResult.Ok },
    )
}
