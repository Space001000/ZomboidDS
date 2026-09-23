package dev.zomboidds.companion.data

import dev.zomboidds.companion.domain.BridgeInfo
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.Container
import dev.zomboidds.companion.domain.ContainerKind
import dev.zomboidds.companion.domain.EquipSlot
import dev.zomboidds.companion.domain.GameEvent
import dev.zomboidds.companion.domain.GameState
import dev.zomboidds.companion.domain.Inventory
import dev.zomboidds.companion.domain.InventoryItem
import dev.zomboidds.companion.domain.ItemAction
import dev.zomboidds.companion.domain.ItemCommand
import dev.zomboidds.companion.domain.ItemMenu
import dev.zomboidds.companion.domain.MenuOption
import dev.zomboidds.companion.domain.PlayerStatus
import dev.zomboidds.companion.domain.SessionInfo
import dev.zomboidds.companion.domain.Vehicle
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Protocol v1 (see protocol/PROTOCOL.md): the one place that knows the wire format.
 * Turns server messages into [GameState] updates.
 */
object ProtocolV1 {

    const val VERSION = 1

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Envelope(val v: Int, val type: String, val seq: Long = 0, val data: JsonElement = JsonNull)

    @Serializable
    private data class HelloDto(val protocol: Int, val bridge: String, val adapter: String)

    @Serializable
    private data class SessionDto(
        val inGame: Boolean,
        val gameVersion: String? = null,
        val capabilities: List<String> = emptyList(),
    )

    @Serializable
    private data class PlayerDto(val health: Float? = null, val bleeding: Boolean = false, val stats: StatsDto = StatsDto())

    @Serializable
    private data class StatsDto(
        val hunger: Float? = null,
        val thirst: Float? = null,
        val fatigue: Float? = null,
        val endurance: Float? = null,
    )

    @Serializable
    private data class InventoryDto(val weight: WeightDto = WeightDto(), val items: List<ItemDto> = emptyList())

    @Serializable
    private data class WeightDto(val current: Float? = null, val max: Float? = null)

    @Serializable
    private data class ItemDto(
        val id: Long,
        val type: String = "",
        val name: String = "",
        val category: String? = null,
        val icon: String? = null,
        val weight: Float? = null,
        val condition: Float? = null,
        val equipped: String? = null,
        val actions: List<String> = emptyList(),
    )

    @Serializable
    private data class VehicleDto(
        val inVehicle: Boolean = false,
        val name: String? = null,
        val speedKmh: Float = 0f,
        val engineRunning: Boolean = false,
        val fuel: Float? = null,
        val isDriver: Boolean = false,
    )

    @Serializable
    private data class ContainersDto(val containers: List<ContainerDto> = emptyList())

    @Serializable
    private data class ContainerDto(
        val id: String,
        val kind: String = "",
        val name: String = "",
        val icon: String? = null,
        val weight: Float? = null,
        val capacity: Float? = null,
        val locked: Boolean = false,
        val items: List<ItemDto>? = null,
    )

    @Serializable
    private data class CommandResultDto(
        val id: String? = null,
        val ok: Boolean,
        val error: String? = null,
        val data: JsonElement? = null,
    )

    /** What a server message means for the app. */
    sealed interface ServerMessage {
        /** Changes the game state (everything except command results). */
        class StateUpdate(val update: (GameState) -> GameState) : ServerMessage

        /**
         * The game's answer to a command we sent. [id] is null if the bridge couldn't read it;
         * [data] is command-specific (e.g. the item menu).
         */
        data class Reply(val id: String?, val result: CommandResult, val data: JsonElement? = null) : ServerMessage

        /** A one-off request from the game, for the UI. */
        data class Event(val event: GameEvent) : ServerMessage
    }

    /** A command for the game: its protocol name and arguments. */
    class Request(val name: String, val args: JsonObject)

    @Serializable
    private data class ShowDto(val panel: String = "", val container: String? = null)

    @Serializable
    private data class MenuDto(val menuId: String, val options: List<MenuOptionDto> = emptyList())

    @Serializable
    private data class MenuOptionDto(
        val id: String,
        val name: String,
        val enabled: Boolean = false,
        val tooltip: String? = null,
        val children: List<MenuOptionDto> = emptyList(),
    )

    /** Throws [IllegalArgumentException] for malformed messages. */
    fun decode(text: String): ServerMessage {
        val envelope = json.decodeFromString<Envelope>(text)
        require(envelope.v == VERSION) { "unsupported protocol version ${envelope.v}" }
        if (envelope.type == "command_result") {
            val dto = json.decodeFromJsonElement<CommandResultDto>(envelope.data)
            val result = if (dto.ok) CommandResult.Ok else CommandResult.Failed(dto.error ?: "the game refused")
            return ServerMessage.Reply(dto.id, result, dto.data?.takeIf { it !is JsonNull })
        }
        if (envelope.type == "show") {
            val dto = json.decodeFromJsonElement<ShowDto>(envelope.data)
            return when (dto.panel) {
                "inventory" -> ServerMessage.Event(GameEvent.ShowInventory(dto.container))
                else -> ServerMessage.StateUpdate { it } // a panel from a newer mod: nothing to show
            }
        }
        return ServerMessage.StateUpdate { state -> apply(state, envelope) }
    }

    fun encode(id: String, command: ItemCommand): String = encode(id, request(command))

    fun encode(id: String, request: Request): String = buildJsonObject {
        put("v", VERSION)
        put("type", "command")
        put("id", id)
        put("name", request.name)
        put("args", request.args)
    }.toString()

    fun itemMenuRequest(itemId: Long) = Request("item_menu", buildJsonObject { put("itemId", JsonPrimitive(itemId)) })

    fun menuSelectRequest(menuId: String, optionId: String) = Request("menu_select", buildJsonObject {
        put("menuId", menuId)
        put("optionId", optionId)
    })

    /** The `data` of an `item_menu` reply. Throws [IllegalArgumentException] if it isn't a menu. */
    fun itemMenu(data: JsonElement?): ItemMenu {
        requireNotNull(data) { "the game sent no menu" }
        val dto = json.decodeFromJsonElement<MenuDto>(data)
        return ItemMenu(dto.menuId, dto.options.map { it.toDomain() })
    }

    private fun MenuOptionDto.toDomain(): MenuOption =
        MenuOption(id, name, enabled, tooltip, children.map { it.toDomain() })

    fun transferRequest(itemId: Long, toContainer: String) = Request("transfer", buildJsonObject {
        put("itemId", JsonPrimitive(itemId))
        put("to", toContainer)
    })

    fun transferAllRequest(fromContainer: String, toContainer: String) = Request("transfer_all", buildJsonObject {
        put("from", fromContainer)
        put("to", toContainer)
    })

    fun request(command: ItemCommand): Request {
        val (name, slot) = when (command.action) {
            ItemAction.EQUIP_PRIMARY -> "equip" to "primary"
            ItemAction.EQUIP_SECONDARY -> "equip" to "secondary"
            ItemAction.EQUIP_BOTH -> "equip" to "both"
            ItemAction.WEAR -> "wear" to null
            ItemAction.UNEQUIP -> "unequip" to null
            ItemAction.DROP -> "drop" to null
        }
        return Request(name, buildJsonObject {
            put("itemId", JsonPrimitive(command.itemId))
            slot?.let { put("slot", it) }
        })
    }

    /**
     * Applies one server message to [state]. Unknown message types (from a newer mod) are ignored.
     * Throws [IllegalArgumentException] for malformed messages.
     */
    fun apply(state: GameState, text: String): GameState =
        when (val message = decode(text)) {
            is ServerMessage.StateUpdate -> message.update(state)
            is ServerMessage.Reply, is ServerMessage.Event -> state
        }

    private fun apply(state: GameState, envelope: Envelope): GameState {
        val data = lenient(envelope.data)
        return when (envelope.type) {
            "hello" -> json.decodeFromJsonElement<HelloDto>(data).let {
                // A (re)connect: whatever we had may be stale; the bridge replays current state next.
                GameState(bridge = BridgeInfo(it.protocol, it.bridge, it.adapter))
            }
            "session" -> json.decodeFromJsonElement<SessionDto>(data).let {
                val session = SessionInfo(it.inGame, it.gameVersion, it.capabilities.toSet())
                if (it.inGame) state.copy(session = session) else GameState(bridge = state.bridge, session = session)
            }
            "player" -> json.decodeFromJsonElement<PlayerDto>(data).let {
                state.copy(player = PlayerStatus(
                    it.health, it.bleeding, it.stats.hunger, it.stats.thirst, it.stats.fatigue, it.stats.endurance))
            }
            "inventory" -> json.decodeFromJsonElement<InventoryDto>(data).let { dto ->
                state.copy(inventory = Inventory(dto.weight.current, dto.weight.max, dto.items.map { it.toDomain() }))
            }
            "vehicle" -> json.decodeFromJsonElement<VehicleDto>(data).let {
                state.copy(vehicle = if (!it.inVehicle) Vehicle.OnFoot else Vehicle.Driving(
                    name = it.name ?: "Vehicle",
                    speedKmh = it.speedKmh,
                    engineRunning = it.engineRunning,
                    fuel = it.fuel,
                    isDriver = it.isDriver,
                ))
            }
            "containers" -> json.decodeFromJsonElement<ContainersDto>(data).let { dto ->
                state.copy(containers = dto.containers.map { it.toDomain() })
            }
            else -> state
        }
    }

    private fun ContainerDto.toDomain() = Container(
        id = id,
        kind = when (kind) {
            "inventory" -> ContainerKind.INVENTORY
            "bag" -> ContainerKind.BAG
            "floor" -> ContainerKind.FLOOR
            else -> ContainerKind.NEARBY // "nearby", or a kind from a newer mod
        },
        name = name.ifEmpty { id },
        icon = icon,
        weight = weight,
        capacity = capacity,
        locked = locked,
        items = if (locked) null else items?.map { it.toDomain() },
    )

    private fun ItemDto.toDomain() = InventoryItem(
        id = id,
        type = type,
        name = name.ifEmpty { type },
        category = category,
        icon = icon,
        weight = weight,
        condition = condition,
        equipped = when (equipped) {
            "primary" -> EquipSlot.PRIMARY
            "secondary" -> EquipSlot.SECONDARY
            "both" -> EquipSlot.BOTH
            "worn" -> EquipSlot.WORN
            else -> null // absent, or a value from a newer mod: treat as not equipped
        },
        actions = actions.mapNotNull(::itemAction), // unknown (newer) actions are skipped
    )

    private fun itemAction(wire: String) = when (wire) {
        "equip.primary" -> ItemAction.EQUIP_PRIMARY
        "equip.secondary" -> ItemAction.EQUIP_SECONDARY
        "equip.both" -> ItemAction.EQUIP_BOTH
        "wear" -> ItemAction.WEAR
        "unequip" -> ItemAction.UNEQUIP
        "drop" -> ItemAction.DROP
        else -> null
    }

    /**
     * Lua has one table type for arrays and objects, so the mod sends an empty table as `[]` even
     * where an object is meant (e.g. `stats` when no stat could be read). For fields that are
     * always objects, treat `[]` as `{}`. Real lists (e.g. `capabilities`) are left alone.
     */
    private val OBJECT_FIELDS = setOf("stats", "weight")

    private fun lenient(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (key, value) ->
            if (key in OBJECT_FIELDS && value is JsonArray && value.isEmpty()) JsonObject(emptyMap()) else lenient(value)
        })
        is JsonArray -> JsonArray(element.map(::lenient))
        else -> element
    }
}
