package dev.zomboidds.companion.domain

/** Everything the app knows about the game. Grows per phase (inventory, vehicle, ...). */
data class GameState(
    val bridge: BridgeInfo? = null,
    val session: SessionInfo? = null,
    val player: PlayerStatus? = null,
    val inventory: Inventory? = null,
)

/** The mod's Java side, from `hello`. */
data class BridgeInfo(val protocol: Int, val version: String, val adapter: String)

/** The game session, from `session`. Not in a game = main menu. */
data class SessionInfo(
    val inGame: Boolean,
    val gameVersion: String? = null,
    val capabilities: Set<String> = emptySet(),
)

/** Values are 0..1 except [health] (0..100). Null means the game didn't report it. */
data class PlayerStatus(
    val health: Float?,
    val bleeding: Boolean,
    val hunger: Float?,
    val thirst: Float?,
    val fatigue: Float?,
    val endurance: Float?,
)

/** The player's main inventory. Weights are in the game's units. */
data class Inventory(val weight: Float?, val maxWeight: Float?, val items: List<InventoryItem>)

data class InventoryItem(
    /** Unique per item instance (two bandages have different ids). */
    val id: Long,
    val type: String,
    val name: String,
    val category: String?,
    /** Texture name for the bridge's icon endpoint, e.g. "Item_Axe". */
    val icon: String?,
    val weight: Float?,
    /** 0..1, only for items where condition matters (weapons, clothing). */
    val condition: Float?,
    val equipped: EquipSlot?,
    /** What the app may offer for this item (decided by the game side). */
    val actions: List<ItemAction> = emptyList(),
)

enum class EquipSlot { PRIMARY, SECONDARY, BOTH, WORN }

sealed interface ConnectionStatus {
    data object Connecting : ConnectionStatus

    data object Connected : ConnectionStatus

    /** Not connected; the next attempt starts in [retryInMs]. The game simply not running is the usual reason. */
    data class Waiting(val reason: String, val retryInMs: Long) : ConnectionStatus
}
