package dev.zomboidds.companion

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class InventoryLayout { GRID, LIST }

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

    private companion object {
        const val KEY_INVENTORY_LAYOUT = "inventoryLayout"
    }
}
