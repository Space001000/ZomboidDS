package dev.zomboidds.companion

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class InventoryLayout { GRID, LIST }

/** SPLIT: your containers on top, the ones around you below. SINGLE: one container at a time. */
enum class ContainerLayout { SPLIT, SINGLE }

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
    }
}
