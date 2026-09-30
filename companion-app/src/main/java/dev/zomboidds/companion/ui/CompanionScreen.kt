package dev.zomboidds.companion.ui

import dev.zomboidds.companion.setup.AppUpdateState
import dev.zomboidds.companion.domain.Crafting
import dev.zomboidds.companion.domain.GameControls
import dev.zomboidds.companion.domain.GameSpeed
import dev.zomboidds.companion.domain.GameEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import coil3.compose.AsyncImage
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.zomboidds.companion.ContainerLayout
import dev.zomboidds.companion.InventoryLayout
import dev.zomboidds.companion.MapPlacement
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
    /** The Command deck's buttons (deck command ids, in order), and how to change them. */
    deckCommands: List<String> = emptyList(),
    onDeckCommandsChange: (List<String>) -> Unit = {},
    map: MapDisplay? = null,
) {
    ZomboidTheme {
        Surface(Modifier.fillMaxSize()) {
            // Keep clear of system bars on ordinary phones (the Thor's bottom screen has none).
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val inGame = connection == ConnectionStatus.Connected && state.session?.inGame == true
                if (inGame) {
                    InGame(state, iconUrl, actions, controls, inventoryDisplay, onInventoryDisplayChange, events, crafting,
                        deckCommands, onDeckCommandsChange, map)
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
 * At most five, so each stays big enough to hit (the bottom screen is small); Map is a sixth only
 * when the player moves the map onto its own tab. While driving, Here is titled Vehicle: the
 * dashboard, with Here's actions (the car's among them) below it.
 */
private enum class Tab(val title: String) { INVENTORY("Inventory"), HERE("Here"), DECK("Deck"), STATUS("Status"), CRAFT("Craft"), MAP("Map") }

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
    deckCommands: List<String>,
    onDeckCommandsChange: (List<String>) -> Unit,
    map: MapDisplay?,
) {
    val driving = state.vehicle as? Vehicle.Driving
    var editingDeck by rememberSaveable { mutableStateOf(false) }
    // Craft only with a mod that can craft.
    val canCraft = crafting != null && "craft" in state.session?.capabilities.orEmpty()
    val mapTab = map?.placement == MapPlacement.OWN_TAB
    val tabs = Tab.entries.filter { (it != Tab.CRAFT || canCraft) && (it != Tab.MAP || mapTab) }
    var tab by rememberSaveable { mutableStateOf(Tab.INVENTORY) }
    // Moving the map takes you along: to its tab, or back to Here where it now sits.
    val onPlacementChange: (MapPlacement) -> Unit = { placement ->
        map?.onPlacementChange(placement)
        if (placement == MapPlacement.OWN_TAB) tab = Tab.MAP else if (tab == Tab.MAP) tab = Tab.HERE
    }
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
        TabBar(tabs, shown, iconUrl, title = { if (it == Tab.HERE && driving != null) "Vehicle" else it.title }, onSelect = { tab = it })
        Box(Modifier.fillMaxSize().padding(10.dp)) {
            when (shown) {
                Tab.HERE -> WithMap(map, onPlacementChange) {
                    if (driving != null) {
                        VehicleTab(driving, state.here, controls, actions, iconUrl)
                    } else {
                        HereScreen(state.here, controls, actions, iconUrl)
                    }
                }
                Tab.MAP -> map?.let { PlayerMiniMap(it, onPlacementChange, Modifier.fillMaxSize()) }
                Tab.INVENTORY -> InventoryScreen(state.inventory, state.containers, iconUrl, actions, inventoryDisplay, onInventoryDisplayChange,
                    show = showRequest, onShowHandled = { showRequest = null })
                Tab.DECK -> if (editingDeck && state.deck != null) {
                    DeckEditor(state.deck.commands, deckCommands, iconUrl, onChange = onDeckCommandsChange,
                        onDone = { editingDeck = false })
                } else {
                    CommandDeckScreen(state.time, state.deck, deckCommands, controls, iconUrl, onEdit = { editingDeck = true })
                }
                Tab.STATUS -> StatusScreen(state, controls, actions, iconUrl)
                Tab.CRAFT -> crafting?.let { CraftScreen(it, iconUrl, changes = state.inventory to state.containers) }
            }
        }
    }
}

/**
 * The app's main navigation. Material's tab row gives every tab the same width, which squeezed
 * "Inventory" onto two lines once the Map tab was added. Here the text tabs share the width and
 * Map is a narrow tab with the game's own sidebar map icon. Looks like Material's primary tabs.
 */
@Composable
private fun TabBar(tabs: List<Tab>, selected: Tab, iconUrl: (String) -> String, title: (Tab) -> String, onSelect: (Tab) -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    Column {
        Row(Modifier.fillMaxWidth().height(48.dp)) {
            tabs.forEach { tab ->
                val isSelected = tab == selected
                var labelWidth by remember { mutableIntStateOf(0) }
                val width = if (tab == Tab.MAP) Modifier.width(64.dp) else Modifier.weight(1f)
                Box(
                    width.fillMaxHeight().selectable(isSelected, role = Role.Tab, onClick = { onSelect(tab) }),
                    contentAlignment = Alignment.Center,
                ) {
                    val measured = Modifier.onSizeChanged { labelWidth = it.width }
                    if (tab == Tab.MAP) {
                        val icon = if (isSelected) "Sidebar/128/Map_On_128" else "Sidebar/128/Map_Off_128"
                        AsyncImage(iconUrl(icon), tab.title, measured.size(30.dp))
                    } else {
                        Text(title(tab), measured, color = color, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    }
                    if (isSelected) {
                        Box(
                            Modifier.align(Alignment.BottomCenter).width(with(LocalDensity.current) { labelWidth.toDp() }).height(3.dp)
                                .background(color, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                        )
                    }
                }
            }
        }
        HorizontalDivider()
    }
}

/** [content] with the map beside it, when the map sits on this tab. */
@Composable
private fun WithMap(map: MapDisplay?, onPlacementChange: (MapPlacement) -> Unit, content: @Composable () -> Unit) {
    if (map == null || map.placement == MapPlacement.OWN_TAB) return content()
    @Composable fun Map() = PlayerMiniMap(map, onPlacementChange, Modifier.width(250.dp).fillMaxHeight())
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (map.placement == MapPlacement.LEFT_OF_HERE) Map()
        Box(Modifier.weight(1f)) { content() }
        if (map.placement == MapPlacement.RIGHT_OF_HERE) Map()
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
            override suspend fun itemMenu(itemIds: List<Long>) = ItemMenuResult.Failed("preview")
            override suspend fun selectMenuOption(menuId: String, optionId: String) = CommandResult.Ok
            override suspend fun transfer(itemId: Long, toContainer: String) = CommandResult.Ok
            override suspend fun transferAll(fromContainer: String, toContainer: String) = CommandResult.Ok
            override suspend fun selectContainer(containerId: String) = CommandResult.Ok
        },
        controls = object : GameControls {
            override suspend fun setSpeed(speed: GameSpeed) = CommandResult.Ok
            override suspend fun runDeckCommand(id: String) = CommandResult.Ok
            override suspend fun drawHotbarSlot(slot: Int) = CommandResult.Ok
            override suspend fun setAlarm(hour: Int, minute: Int, on: Boolean) = CommandResult.Ok
            override fun watchHere(on: Boolean) {}
            override suspend fun bodyPartMenu(partId: String) = ItemMenuResult.Failed("preview")
        },
        inventoryDisplay = InventoryDisplay(InventoryLayout.GRID, ContainerLayout.SPLIT),
        onInventoryDisplayChange = {},
        events = emptyFlow(),
    )
}
