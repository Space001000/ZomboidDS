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
    /** Injuries, as the game's health panel lists them; null until the game reported them. */
    val health: Health? = null,
    /** The game's moodles (hungry, bleeding, ...); null until the game reported them. */
    val moodles: Moodles? = null,
    /** The Command deck's clock and commands; null until the game reported them (or an older mod). */
    val deck: DeckState? = null,
    /** Where the player is on the map, and what the save allows; null until the game reported it (or an older mod). */
    val mapPosition: MapPosition? = null,
    /** The parts of the map the player has seen; null until the game reported them. */
    val explored: ExploredAreas? = null,
    /** The symbols and notes the player put on their map; empty until the game reported them. */
    val mapSymbols: List<MapSymbol> = emptyList(),
)

/** What the game's moodle column shows, most urgent first, and the images to draw them with. */
data class Moodles(val list: List<Moodle>, val background: String? = null, val border: String? = null)

/**
 * A moodle as the game shows it: [name] and [description] are its own texts for the [level]
 * (1-4); [color] is its background colour (red, green, blue; 0-1), [icon] its image.
 */
data class Moodle(
    val id: String,
    val name: String,
    val description: String?,
    val level: Int,
    val tone: MoodleTone,
    val color: List<Float>,
    val icon: String?,
)

enum class MoodleTone { GOOD, BAD, NEUTRAL }

/** The body parts the game's health panel lists, and which body silhouette to draw. */
data class Health(val parts: List<BodyPartStatus>, val female: Boolean = false)

/** A body part the game's health panel lists, with its lines ("Scratched (Severe)", "Bandaged"). */
data class BodyPartStatus(val id: String, val name: String, val lines: List<HealthLine>) {
    /** The most urgent of its lines: a problem, then needs attention, then treated. */
    val tone: HealthTone
        get() = listOf(HealthTone.BAD, HealthTone.WARN, HealthTone.GOOD).firstOrNull { t -> lines.any { it.tone == t } }
            ?: HealthTone.NEUTRAL

    /** Something to do about it (a problem or needs attention), as opposed to treated. */
    val needsTreatment: Boolean get() = tone == HealthTone.BAD || tone == HealthTone.WARN
}

data class HealthLine(val text: String, val tone: HealthTone)

/** From the game's colours: a problem, treated, or needs attention (dirty bandage, infection). */
enum class HealthTone { BAD, GOOD, WARN, NEUTRAL }

/** The game's world menu for where the player stands, or why there is none (e.g. paused). */
data class HereState(val menu: ItemMenu?, val unavailable: String?)

/** The game's speed buttons (top right in the game). */
enum class GameSpeed(val multiplier: Int) { PAUSED(0), PLAY(1), FAST(5), FASTER(20), WAIT(40) }

/**
 * [canChange] is false in multiplayer, where the game doesn't allow it either. [gameMenuOpen]: the
 * game's pause menu (Esc) is open, and speed changes wait until it's closed, as in the game.
 */
data class TimeState(val speed: GameSpeed?, val canChange: Boolean, val gameMenuOpen: Boolean = false)

/**
 * The Command deck: the game's clock (only with a watch, as in the game), the commands it can run,
 * the player's hotbar (for Weapons) and the watch whose alarm the Alarm command sets.
 */
data class DeckState(
    val clock: DeckClock?,
    val commands: List<DeckCommand>,
    val hotbar: List<HotbarSlot> = emptyList(),
    val alarmClock: AlarmClock? = null,
)

/** A slot of the game's hotbar (Back, Belt Left, ...), in its order; [item] null when empty. */
data class HotbarSlot(val slot: Int, val name: String, val item: HotbarItem?, val inHand: Boolean)

data class HotbarItem(val name: String, val icon: String?)

/** The watch or clock the game's alarm uses, and its alarm. */
data class AlarmClock(val name: String, val hour: Int, val minute: Int, val on: Boolean)

/** As the game's own clock shows it: [time] in the player's 12/24-hour setting; [date] and [alarm] if shown. */
data class DeckClock(val time: String, val date: String?, val alarm: String?)

/**
 * A command the game can run, like its key binding does. [id] is stable (the player's Deck is
 * remembered by it); [name] is the game's own; [on] is set for modes (search mode, flashlight).
 */
data class DeckCommand(val id: String, val name: String, val icon: String?, val available: Boolean, val on: Boolean? = null)

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
    /** Food that goes off: how fresh, as the game names it; null for everything else. */
    val freshness: Freshness? = null,
    /** The name without the game's bracketed state ("Steak" for "Steak (Fresh, Cooked)"), when they differ. */
    val shortName: String? = null,
    /** The freshness word as it appears in [name] ("Stale"). */
    val freshnessText: String? = null,
    val cooking: Cooking? = null,
    val fluid: FluidFill? = null,
    /** Read, watched or heard already (the game ticks it). */
    val read: Boolean = false,
    /** Set Unwanted in the game (it greys these out). */
    val unwanted: Boolean = false,
)

/** The name for a tile, where only a few letters fit. */
val InventoryItem.tileName: String get() = shortName ?: name

enum class Freshness { FRESH, STALE, ROTTEN }

enum class CookState { COOKED, UNCOOKED, BURNT }

/**
 * Food's cooking: its [state] with the word the game's name uses ([text]: Cooked, Grilled,
 * Uncooked, Burnt...), and while it heats how far along it is: cooking up to done, then
 * [burning] up to burnt ([progress] 0..1, null when it isn't heating).
 */
data class Cooking(val state: CookState?, val text: String?, val progress: Float? = null, val burning: Boolean = false)

/**
 * A fluid container: [amount] and [capacity] in litres, the main fluid's [name] (or a [mixture]),
 * and the colour the game draws it in (0..1 rgb).
 */
data class FluidFill(
    val amount: Float,
    val capacity: Float,
    val name: String? = null,
    val mixture: Boolean = false,
    val color: Triple<Float, Float, Float>? = null,
) {
    val fraction: Float get() = if (capacity > 0f) (amount / capacity).coerceIn(0f, 1f) else 0f

    /** "0.3 / 0.6 L", as the game's tooltip writes it (FluidUtil.getFractionFormatted). */
    fun amountText(): String {
        val format = java.text.DecimalFormat(
            when {
                maxOf(amount, capacity) >= 1000f -> "#"
                maxOf(amount, capacity) >= 10f -> "#.#"
                else -> "#.##"
            },
        )
        return "${format.format(amount.toDouble())} / ${format.format(capacity.toDouble())} L"
    }
}

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
    /** The game's loot window has it selected: the container the game outlines in the world. */
    val selected: Boolean = false,
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
