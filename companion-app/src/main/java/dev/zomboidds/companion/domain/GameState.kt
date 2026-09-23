package dev.zomboidds.companion.domain

/** Everything the app knows about the game. Grows per phase (inventory, vehicle, ...). */
data class GameState(
    val bridge: BridgeInfo? = null,
    val session: SessionInfo? = null,
    val player: PlayerStatus? = null,
    val inventory: Inventory? = null,
    /** Null until the game reported it. */
    val vehicle: Vehicle? = null,
    /** Your inventory, bags and everything within reach; null until the game reported it. */
    val containers: List<Container>? = null,
    /** The game's speed; null until the game reported it (or an older mod that doesn't). */
    val time: TimeState? = null,
    /** "Here" while the app watches it ([GameControls.watchHere]); null otherwise. */
    val here: HereState? = null,
)

/** The game's world menu for where the player stands, or why there is none (e.g. paused). */
data class HereState(val menu: ItemMenu?, val unavailable: String?)

/** The game's speed buttons (top right in the game). */
enum class GameSpeed(val multiplier: Int) { PAUSED(0), PLAY(1), FAST(5), FASTER(20), WAIT(40) }

/**
 * [canChange] is false in multiplayer, where the game doesn't allow it either. [gameMenuOpen]: the
 * game's pause menu (Esc) is open, and speed changes wait until it's closed, as in the game.
 */
data class TimeState(val speed: GameSpeed?, val canChange: Boolean, val gameMenuOpen: Boolean = false)

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

/**
 * A container the player can use right now, as the game's inventory and loot windows list it.
 * Commands refer to it by [id], which stays the same while the container exists.
 */
data class Container(
    val id: String,
    val kind: ContainerKind,
    val name: String,
    val icon: String?,
    val weight: Float?,
    val capacity: Float?,
    val locked: Boolean,
    /**
     * Null for the main inventory (its items are in [GameState.inventory]) and for locked
     * containers (they can't be looked into).
     */
    val items: List<InventoryItem>?,
)

enum class ContainerKind {
    /** The player's main inventory. */
    INVENTORY,

    /** A bag the player carries. */
    BAG,

    /** Furniture, corpses, vehicles, bags on the ground, ... within reach. */
    NEARBY,

    FLOOR,
}

/** The vehicle the player is in, or [OnFoot]. */
sealed interface Vehicle {
    data object OnFoot : Vehicle

    data class Driving(
        val name: String,
        val speedKmh: Float,
        val engineRunning: Boolean,
        /** 0..1, null if the vehicle has no (readable) tank. */
        val fuel: Float?,
        /** False when the player is a passenger. */
        val isDriver: Boolean,
    ) : Vehicle
}

sealed interface ConnectionStatus {
    data object Connecting : ConnectionStatus

    data object Connected : ConnectionStatus

    /** Not connected; the next attempt starts in [retryInMs]. The game simply not running is the usual reason. */
    data class Waiting(val reason: String, val retryInMs: Long) : ConnectionStatus
}
