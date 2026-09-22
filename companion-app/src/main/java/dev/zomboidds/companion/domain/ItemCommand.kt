package dev.zomboidds.companion.domain

/** Something the player can do with an item. Which ones apply is decided by the game side. */
enum class ItemAction { EQUIP_PRIMARY, EQUIP_SECONDARY, EQUIP_BOTH, WEAR, UNEQUIP, DROP }

data class ItemCommand(val itemId: Long, val action: ItemAction)

sealed interface CommandResult {
    /** The game accepted it (usually queued as a timed action; the next inventory update shows the effect). */
    data object Ok : CommandResult

    data class Failed(val reason: String) : CommandResult
}
