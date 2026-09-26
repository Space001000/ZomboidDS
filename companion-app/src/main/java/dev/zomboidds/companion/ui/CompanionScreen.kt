package dev.zomboidds.companion.ui

import dev.zomboidds.companion.setup.AppUpdateState
import dev.zomboidds.companion.domain.Crafting
import dev.zomboidds.companion.domain.GameControls
import dev.zomboidds.companion.domain.GameSpeed
import dev.zomboidds.companion.domain.GameEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.zomboidds.companion.ContainerLayout
import dev.zomboidds.companion.InventoryLayout
import dev.zomboidds.companion.domain.BridgeInfo
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.ItemActions
import dev.zomboidds.companion.domain.ItemCommand
import dev.zomboidds.companion.domain.ItemMenuResult
import dev.zomboidds.companion.domain.ConnectionStatus
import dev.zomboidds.companion.domain.GameState
import dev.zomboidds.companion.domain.PlayerStatus
import dev.zomboidds.companion.domain.SessionInfo
import dev.zomboidds.companion.domain.Vehicle
import dev.zomboidds.companion.setup.SetupReport


@Composable
fun CompanionScreen(
    state: GameState,
    connection: ConnectionStatus,
    setup: SetupReport,
    update: AppUpdateState,
    setupActions: SetupActions,
    iconUrl: (String) -> String,
    actions: ItemActions,
    controls: GameControls,
    inventoryDisplay: InventoryDisplay,
    onInventoryDisplayChange: (InventoryDisplay) -> Unit,
    events: Flow<GameEvent>,
    crafting: Crafting? = null,
) {
    ZomboidTheme {
        Surface(Modifier.fillMaxSize()) {
            // Keep clear of system bars on ordinary phones (the Thor's bottom screen has none).
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val inGame = connection == ConnectionStatus.Connected && state.session?.inGame == true
                if (inGame) {
                    InGame(state, iconUrl, actions, controls, inventoryDisplay, onInventoryDisplayChange, events, crafting)
                } else {
                    // Outside a game is when setup matters: show what's left to do.
                    var licences by remember { mutableStateOf(false) }
                    Column(
                        Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        StatusHeader(state, connection)
                        SetupChecklist(setup, update, gameStep(connection), setupActions)
                        AboutCard(openUrl = setupActions::openUrl, onLicences = { licences = true })
                    }
                    if (licences) LicencesPanel(onClose = { licences = false })
                }
            }
        }
    }
}

/**
 * At most five, so each stays big enough to hit (the bottom screen is small). While driving, Here
 * is titled Vehicle: the dashboard, with Here's actions (the car's among them) below it.
 */
private enum class Tab(val title: String) { INVENTORY("Inventory"), HERE("Here"), STATUS("Status"), CRAFT("Craft"), DECK("Deck") }

@Composable
private fun InGame(
    state: GameState,
    iconUrl: (String) -> String,
    actions: ItemActions,
    controls: GameControls,
    inventoryDisplay: InventoryDisplay,
    onInventoryDisplayChange: (InventoryDisplay) -> Unit,
    events: Flow<GameEvent>,
    crafting: Crafting?,
) {
    val driving = state.vehicle as? Vehicle.Driving
    // Craft only with a mod that can craft.
    val canCraft = crafting != null && "craft" in state.session?.capabilities.orEmpty()
    val tabs = Tab.entries.filter { it != Tab.CRAFT || canCraft }
    var tab by rememberSaveable { mutableStateOf(Tab.INVENTORY) }
    var tabBeforeVehicle by rememberSaveable { mutableStateOf(Tab.INVENTORY) }

    // Getting in switches to the dashboard (Here, titled Vehicle); getting out returns to where the player was.
    val inVehicle = driving != null
    LaunchedEffect(inVehicle) {
        if (inVehicle && tab != Tab.HERE) {
            tabBeforeVehicle = tab
            tab = Tab.HERE
        } else if (!inVehicle && tab == Tab.HERE) {
            tab = tabBeforeVehicle
        }
    }
    val shown = if (tab in tabs) tab else Tab.INVENTORY

    // The game's Loot/Inventory button: show that container here instead of on the top screen.
    var showRequest by remember { mutableStateOf<ShowRequest?>(null) }
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is GameEvent.ShowInventory -> {
                    tab = Tab.INVENTORY
                    showRequest = ShowRequest(event.containerId)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tabs.indexOf(shown)) {
            tabs.forEach {
                val title = if (it == Tab.HERE && driving != null) "Vehicle" else it.title
                Tab(selected = shown == it, onClick = { tab = it }, text = { Text(title) })
            }
        }
        Box(Modifier.fillMaxSize().padding(10.dp)) {
            when (shown) {
                Tab.HERE -> if (driving != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        VehicleScreen(driving, compact = true)
                        HereScreen(state.here, controls, actions, iconUrl, Modifier.weight(1f))
                    }
                } else {
                    HereScreen(state.here, controls, actions, iconUrl)
                }
                Tab.INVENTORY -> InventoryScreen(state.inventory, state.containers, iconUrl, actions, inventoryDisplay, onInventoryDisplayChange,
                    show = showRequest, onShowHandled = { showRequest = null })
                Tab.DECK -> CommandDeckScreen(state.time, controls, iconUrl)
                Tab.STATUS -> StatusScreen(state, controls, actions, iconUrl)
                Tab.CRAFT -> crafting?.let { CraftScreen(it, iconUrl, changes = state.inventory to state.containers) }
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
internal fun PlayerCard(player: PlayerStatus, showHealth: Boolean = true) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (showHealth) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Health", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (player.bleeding) {
                    Text("BLEEDING", color = Danger, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                }
                Text(player.health?.let { "%.0f".format(it) } ?: "?", style = MaterialTheme.typography.headlineSmall)
            }
            if (showHealth) Meter(player.health?.div(100f), if ((player.health ?: 100f) < 50f) Danger else Good)
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
        update = AppUpdateState.UpToDate("1.0.0"),
        setupActions = object : SetupActions {
            override fun grantAccess() {}
            override fun selectInstance(name: String) {}
            override fun getZombieBuddy() {}
            override fun openZomdroid() {}
            override fun installMod() {}
            override fun enableForNewGames() {}
            override fun checkForUpdate() {}
            override fun downloadUpdate(release: dev.zomboidds.companion.setup.AppRelease) {}
            override fun installUpdate(file: java.io.File) {}
            override fun openUrl(url: String) {}
        },
        iconUrl = { it },
        actions = object : ItemActions {
            override suspend fun perform(command: ItemCommand) = CommandResult.Ok
            override suspend fun itemMenu(itemId: Long) = ItemMenuResult.Failed("preview")
            override suspend fun selectMenuOption(menuId: String, optionId: String) = CommandResult.Ok
            override suspend fun transfer(itemId: Long, toContainer: String) = CommandResult.Ok
            override suspend fun transferAll(fromContainer: String, toContainer: String) = CommandResult.Ok
            override suspend fun selectContainer(containerId: String) = CommandResult.Ok
        },
        controls = object : GameControls {
            override suspend fun setSpeed(speed: GameSpeed) = CommandResult.Ok
            override suspend fun runDeckCommand(id: String) = CommandResult.Ok
            override fun watchHere(on: Boolean) {}
            override suspend fun bodyPartMenu(partId: String) = ItemMenuResult.Failed("preview")
        },
        inventoryDisplay = InventoryDisplay(InventoryLayout.GRID, ContainerLayout.SPLIT),
        onInventoryDisplayChange = {},
        events = emptyFlow(),
    )
}
