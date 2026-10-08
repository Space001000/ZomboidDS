package dev.zomboidds.companion

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class InventoryLayout { GRID, LIST }

/**
 * SPLIT: your containers on top, the ones around you below. SIDE_BY_SIDE: yours on the left, the
 * ones around you on the right. SINGLE: one container at a time.
 */
enum class ContainerLayout { SPLIT, SIDE_BY_SIDE, SINGLE }

/** Where the map sits: beside the Here/Vehicle tab's content, or on a tab of its own. */
enum class MapPlacement { LEFT_OF_HERE, RIGHT_OF_HERE, OWN_TAB }

/** What the Craft tab shows: the game's crafting window or its build window (the tab's ▾ menu). */
enum class CraftMode(val title: String) { CRAFT("Craft"), BUILD("Build") }

/** The user's display choices, kept across app restarts. */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _inventoryLayout = MutableStateFlow(
        InventoryLayout.entries.firstOrNull { it.name == prefs.getString(KEY_INVENTORY_LAYOUT, null) }
            ?: InventoryLayout.GRID)
    val inventoryLayout: StateFlow<InventoryLayout> = _inventoryLayout.asStateFlow()

    fun setInventoryLayout(layout: InventoryLayout) {
        prefs.edit().putString(KEY_INVENTORY_LAYOUT, layout.name).apply()
        _inventoryLayout.value = layout
    }

    private val _containerLayout = MutableStateFlow(
        ContainerLayout.entries.firstOrNull { it.name == prefs.getString(KEY_CONTAINER_LAYOUT, null) }
            ?: ContainerLayout.SPLIT)
    val containerLayout: StateFlow<ContainerLayout> = _containerLayout.asStateFlow()

    fun setContainerLayout(layout: ContainerLayout) {
        prefs.edit().putString(KEY_CONTAINER_LAYOUT, layout.name).apply()
        _containerLayout.value = layout
    }

    private val _mapPlacement = MutableStateFlow(
        MapPlacement.entries.firstOrNull { it.name == prefs.getString(KEY_MAP_PLACEMENT, null) }
            ?: MapPlacement.LEFT_OF_HERE)
    val mapPlacement: StateFlow<MapPlacement> = _mapPlacement.asStateFlow()

    fun setMapPlacement(placement: MapPlacement) {
        prefs.edit().putString(KEY_MAP_PLACEMENT, placement.name).apply()
        _mapPlacement.value = placement
    }

    private val _craftMode = MutableStateFlow(
        CraftMode.entries.firstOrNull { it.name == prefs.getString(KEY_CRAFT_MODE, null) } ?: CraftMode.CRAFT)
    val craftMode: StateFlow<CraftMode> = _craftMode.asStateFlow()

    fun setCraftMode(mode: CraftMode) {
        prefs.edit().putString(KEY_CRAFT_MODE, mode.name).apply()
        _craftMode.value = mode
    }

    private val _mapSymbols = MutableStateFlow(prefs.getBoolean(KEY_MAP_SYMBOLS, false))

    /** Show the player's own map symbols and notes on the map. Off by default, like the game's minimap. */
    val mapSymbols: StateFlow<Boolean> = _mapSymbols.asStateFlow()

    fun setMapSymbols(show: Boolean) {
        prefs.edit().putBoolean(KEY_MAP_SYMBOLS, show).apply()
        _mapSymbols.value = show
    }

    private val _whatsNewSeen = MutableStateFlow(prefs.getString(KEY_WHATS_NEW_SEEN, null))

    /** The app version whose "What's new" the user closed; null on a new install. */
    val whatsNewSeen: StateFlow<String?> = _whatsNewSeen.asStateFlow()

    fun setWhatsNewSeen(version: String) {
        prefs.edit().putString(KEY_WHATS_NEW_SEEN, version).apply()
        _whatsNewSeen.value = version
    }

    private val _deckCommands = MutableStateFlow(
        prefs.getString(KEY_DECK_COMMANDS, null)?.split(',')?.filter { it.isNotBlank() } ?: DEFAULT_DECK_COMMANDS)

    /** The Command deck's buttons, in order: ids of the game's deck commands. */
    val deckCommands: StateFlow<List<String>> = _deckCommands.asStateFlow()

    fun setDeckCommands(ids: List<String>) {
        prefs.edit().putString(KEY_DECK_COMMANDS, ids.joinToString(",")).apply()
        _deckCommands.value = ids
    }

    companion object {
        /** A new install's Deck (the user's pick, 2026-09-26; Weapons added first). */
        val DEFAULT_DECK_COMMANDS = listOf("weapons", "zoom_in", "zoom_out", "search_mode", "flashlight")

        private const val KEY_INVENTORY_LAYOUT = "inventoryLayout"
        private const val KEY_CONTAINER_LAYOUT = "containerLayout"
        private const val KEY_DECK_COMMANDS = "deckCommands"
        private const val KEY_MAP_PLACEMENT = "mapPlacement"
        private const val KEY_WHATS_NEW_SEEN = "whatsNewSeen"
        private const val KEY_MAP_SYMBOLS = "mapSymbols"
        private const val KEY_CRAFT_MODE = "craftMode"
    }
}
