package dev.zomboidds.companion.data

import dev.zomboidds.companion.domain.AlarmClock
import dev.zomboidds.companion.domain.BodyPartStatus
import dev.zomboidds.companion.domain.BridgeInfo
import dev.zomboidds.companion.domain.BuildList
import dev.zomboidds.companion.domain.BuildRecipe
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.Container
import dev.zomboidds.companion.domain.ContainerKind
import dev.zomboidds.companion.domain.CookState
import dev.zomboidds.companion.domain.Cooking
import dev.zomboidds.companion.domain.DeckClock
import dev.zomboidds.companion.domain.DeckCommand
import dev.zomboidds.companion.domain.DeckState
import dev.zomboidds.companion.domain.EquipSlot
import dev.zomboidds.companion.domain.ExploredAreas
import dev.zomboidds.companion.domain.Fabric
import dev.zomboidds.companion.domain.FluidFill
import dev.zomboidds.companion.domain.Freshness
import dev.zomboidds.companion.domain.GameEvent
import dev.zomboidds.companion.domain.GameSpeed
import dev.zomboidds.companion.domain.GameState
import dev.zomboidds.companion.domain.Garment
import dev.zomboidds.companion.domain.GarmentPart
import dev.zomboidds.companion.domain.GarmentSummary
import dev.zomboidds.companion.domain.Health
import dev.zomboidds.companion.domain.HealthLine
import dev.zomboidds.companion.domain.HealthTone
import dev.zomboidds.companion.domain.HereState
import dev.zomboidds.companion.domain.HotbarItem
import dev.zomboidds.companion.domain.HotbarSlot
import dev.zomboidds.companion.domain.Inventory
import dev.zomboidds.companion.domain.InventoryItem
import dev.zomboidds.companion.domain.ItemAction
import dev.zomboidds.companion.domain.ItemCommand
import dev.zomboidds.companion.domain.ItemMenu
import dev.zomboidds.companion.domain.MapPosition
import dev.zomboidds.companion.domain.MapSymbol
import dev.zomboidds.companion.domain.MenuOption
import dev.zomboidds.companion.domain.MenuPill
import dev.zomboidds.companion.domain.Moodle
import dev.zomboidds.companion.domain.MoodleTone
import dev.zomboidds.companion.domain.Moodles
import dev.zomboidds.companion.domain.Placing
import dev.zomboidds.companion.domain.PlayerStatus
import dev.zomboidds.companion.domain.RecipeCategory
import dev.zomboidds.companion.domain.RecipeDetails
import dev.zomboidds.companion.domain.RecipeInput
import dev.zomboidds.companion.domain.RecipeList
import dev.zomboidds.companion.domain.RecipeOutput
import dev.zomboidds.companion.domain.RecipeSkill
import dev.zomboidds.companion.domain.RecipeSummary
import dev.zomboidds.companion.domain.SessionInfo
import dev.zomboidds.companion.domain.Sewing
import dev.zomboidds.companion.domain.SewingKit
import dev.zomboidds.companion.domain.SkillNeed
import dev.zomboidds.companion.domain.TailorList
import dev.zomboidds.companion.domain.TimeState
import dev.zomboidds.companion.domain.Vehicle
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Inflater
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put

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
        val freshness: String? = null,
        val shortName: String? = null,
        val freshnessText: String? = null,
        val cooking: CookingDto? = null,
        val fluid: FluidDto? = null,
        val read: Boolean = false,
        val unwanted: Boolean = false,
    )

    @Serializable
    private data class CookingDto(val state: String? = null, val text: String? = null, val progress: Float? = null, val burning: Boolean = false)

    @Serializable
    private data class FluidDto(
        val amount: Float = 0f,
        val capacity: Float = 0f,
        val name: String? = null,
        val mixture: Boolean = false,
        val color: List<Float>? = null,
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
    private data class HealthDto(val parts: List<BodyPartDto> = emptyList(), val female: Boolean = false)

    @Serializable
    private data class BodyPartDto(val id: String, val name: String = "", val lines: List<HealthLineDto> = emptyList())

    @Serializable
    private data class HealthLineDto(val text: String, val tone: String? = null)

    @Serializable
    private data class MoodlesDto(val moodles: List<MoodleDto> = emptyList(), val background: String? = null, val border: String? = null)

    @Serializable
    private data class MoodleDto(
        val id: String,
        val name: String = "",
        val description: String? = null,
        val level: Int = 1,
        val tone: String? = null,
        val color: List<Float> = emptyList(),
        val icon: String? = null,
    )

    @Serializable
    private data class HereDto(
        val watching: Boolean = false,
        val menuId: String? = null,
        val options: List<MenuOptionDto> = emptyList(),
        val unavailable: String? = null,
    )

    @Serializable
    private data class DeckDto(
        val clock: DeckClockDto? = null,
        val commands: List<DeckCommandDto> = emptyList(),
        val hotbar: List<HotbarSlotDto> = emptyList(),
        val alarmClock: AlarmClockDto? = null,
    )

    @Serializable
    private data class HotbarSlotDto(val slot: Int? = null, val name: String? = null, val item: HotbarItemDto? = null, val inHand: Boolean = false)

    @Serializable
    private data class HotbarItemDto(val name: String? = null, val icon: String? = null)

    @Serializable
    private data class AlarmClockDto(val name: String? = null, val hour: Int? = null, val minute: Int? = null, val on: Boolean = false)

    @Serializable
    private data class DeckClockDto(val time: String? = null, val date: String? = null, val alarm: String? = null)

    @Serializable
    private data class DeckCommandDto(
        val id: String? = null,
        val name: String? = null,
        val icon: String? = null,
        val available: Boolean = true,
        val on: Boolean? = null,
    )

    @Serializable
    private data class BuildingDto(val placing: PlacingDto? = null)

    @Serializable
    private data class PlacingDto(
        val id: String,
        val name: String = "",
        val icon: String? = null,
        val blocked: Boolean = false,
        val missing: List<String> = emptyList(),
    )

    @Serializable
    private data class MapDto(
        val x: Float? = null,
        val y: Float? = null,
        val z: Int = 0,
        val heading: Int? = null,
        val miniMap: Boolean = false,
        val worldMap: Boolean = false,
        val alwaysShow: Boolean = false,
    )

    @Serializable
    private data class ExploredDto(
        val originX: Int = 0,
        val originY: Int = 0,
        val unit: Int = 32,
        val width: Int = 0,
        val height: Int = 0,
        val bits: String = "",
    )

    @Serializable
    private data class MapSymbolsDto(val symbols: List<MapSymbolDto> = emptyList())

    @Serializable
    private data class MapSymbolDto(
        val kind: String? = null,
        val icon: String? = null,
        val text: String? = null,
        val x: Float = 0f,
        val y: Float = 0f,
        val color: List<Float> = emptyList(),
        val scale: Float = 0.666f,
        val rotation: Float = 0f,
        val anchorX: Float = 0.5f,
        val anchorY: Float = 0.5f,
        val label: Boolean = false,
        val minZoom: Float = 0f,
        val maxZoom: Float = 24f,
    )

    /** The game's seen-areas bit field: base64 of zlib-deflated bytes (the bridge's ExploredAreas). */
    private fun inflate(base64: String): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(Base64.getDecoder().decode(base64))
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }

    @Serializable
    private data class TimeDto(val speed: Int? = null, val canChange: Boolean = false, val gameMenuOpen: Boolean = false)

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
        val selected: Boolean = false,
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
    private data class ShowDto(val panel: String = "", val container: String? = null, val item: Long? = null)

    @Serializable
    private data class MenuDto(val menuId: String, val options: List<MenuOptionDto> = emptyList())

    @Serializable
    private data class MenuOptionDto(
        val id: String,
        val name: String,
        val enabled: Boolean = false,
        val tooltip: String? = null,
        val children: List<MenuOptionDto> = emptyList(),
        val icon: String? = null,
        val pill: String? = null,
        val key: String? = null,
        val tray: Boolean = false,
        val front: Boolean = false,
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
                "garment" if dto.item != null -> ServerMessage.Event(GameEvent.ShowGarment(dto.item))
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

    fun bodyPartMenuRequest(partId: String) = Request("health_menu", buildJsonObject { put("part", partId) })

    fun watchHereRequest(on: Boolean) = Request("watch_here", buildJsonObject { put("on", on) })

    /** `itemId` is the first (what a mod before 0.21 reads); `itemIds` all of them, when several. */
    fun itemMenuRequest(itemIds: List<Long>) = Request("item_menu", buildJsonObject {
        put("itemId", JsonPrimitive(itemIds.first()))
        if (itemIds.size > 1) put("itemIds", JsonArray(itemIds.map { JsonPrimitive(it) }))
    })

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
        MenuOption(
            id, name, enabled, tooltip, children.map { it.toDomain() }, icon,
            pill = when (pill) {
                null -> null
                "drop" -> MenuPill.DROP
                else -> MenuPill.ACTION
            },
            key = key, tray = tray, front = front,
        )

    fun craftListRequest() = Request("craft_list", buildJsonObject { })

    fun craftRecipeRequest(id: String) = Request("craft_recipe", buildJsonObject { put("recipe", id) })

    fun craftRequest(id: String, count: Int) = Request("craft", buildJsonObject {
        put("recipe", id)
        put("count", count)
    })

    @Serializable
    private data class RecipeListDto(val recipes: List<RecipeSummaryDto> = emptyList(), val categories: List<RecipeCategoryDto> = emptyList())

    @Serializable
    private data class RecipeSummaryDto(
        val id: String,
        val name: String = "",
        val icon: String? = null,
        val category: String? = null,
        val canCraft: Boolean = false,
    )

    @Serializable
    private data class RecipeCategoryDto(val id: String, val name: String = "")

    @Serializable
    private data class RecipeDetailsDto(
        val id: String,
        val name: String = "",
        val icon: String? = null,
        val category: String? = null,
        val seconds: Float? = null,
        val canCraft: Boolean = false,
        /** `build_recipe` says canBuild. */
        val canBuild: Boolean = false,
        val max: Int = 0,
        val inputs: List<RecipeInputDto> = emptyList(),
        val outputs: List<RecipeOutputDto> = emptyList(),
        val skills: List<RecipeSkillDto> = emptyList(),
    )

    @Serializable
    private data class RecipeInputDto(
        val name: String? = null,
        val icon: String? = null,
        val need: Float = 1f,
        val have: Float = 0f,
        val ok: Boolean = false,
        val keep: Boolean = false,
        val others: Int = 0,
        val unit: String? = null,
    )

    @Serializable
    private data class RecipeOutputDto(val name: String? = null, val icon: String? = null, val amount: Float = 1f, val unit: String? = null)

    @Serializable
    private data class RecipeSkillDto(val name: String? = null, val level: Int = 0, val have: Int = 0)

    /** The `data` of a `craft_list` reply. Throws [IllegalArgumentException] if it isn't one. */
    fun recipeList(data: JsonElement?): RecipeList {
        requireNotNull(data) { "the game sent no recipes" }
        val dto = json.decodeFromJsonElement<RecipeListDto>(lenient(data))
        return RecipeList(
            dto.recipes.map { RecipeSummary(it.id, it.name.ifEmpty { it.id }, it.icon, it.category, it.canCraft) },
            dto.categories.map { RecipeCategory(it.id, it.name.ifEmpty { it.id }) },
        )
    }

    /** The `data` of a `craft_recipe` reply. Throws [IllegalArgumentException] if it isn't one. */
    fun recipeDetails(data: JsonElement?): RecipeDetails {
        requireNotNull(data) { "the game sent no recipe" }
        val dto = json.decodeFromJsonElement<RecipeDetailsDto>(lenient(data))
        return RecipeDetails(
            id = dto.id,
            name = dto.name.ifEmpty { dto.id },
            icon = dto.icon,
            category = dto.category,
            seconds = dto.seconds?.toInt(),
            canCraft = dto.canCraft || dto.canBuild,
            max = dto.max,
            inputs = dto.inputs.map { RecipeInput(it.name ?: "?", it.icon, it.need, it.have, it.ok, it.keep, it.others, it.unit) },
            outputs = dto.outputs.map { RecipeOutput(it.name ?: "?", it.icon, it.amount, it.unit) },
            skills = dto.skills.map { RecipeSkill(it.name ?: "?", it.level, it.have) },
        )
    }

    fun buildListRequest() = Request("build_list", buildJsonObject { })

    fun buildRecipeRequest(id: String) = Request("build_recipe", buildJsonObject { put("recipe", id) })

    fun buildPlaceRequest(id: String) = Request("build_place", buildJsonObject { put("recipe", id) })

    fun buildStopRequest() = Request("build_stop", buildJsonObject { })

    @Serializable
    private data class BuildListDto(val recipes: List<BuildRecipeDto> = emptyList(), val categories: List<RecipeCategoryDto> = emptyList())

    @Serializable
    private data class BuildRecipeDto(
        val id: String,
        val name: String = "",
        val icon: String? = null,
        val category: String? = null,
        val canBuild: Boolean = false,
        val group: String? = null,
        val level: Int? = null,
        val version: String? = null,
        val groupName: String? = null,
        val skill: SkillNeedDto? = null,
    )

    @Serializable
    private data class SkillNeedDto(val name: String? = null, val level: Int = 0)

    /** The `data` of a `build_list` reply. Throws [IllegalArgumentException] if it isn't one. */
    fun buildList(data: JsonElement?): BuildList {
        requireNotNull(data) { "the game sent no recipes" }
        val dto = json.decodeFromJsonElement<BuildListDto>(lenient(data))
        return BuildList(
            dto.recipes.map {
                BuildRecipe(it.id, it.name.ifEmpty { it.id }, it.icon, it.category, it.canBuild, it.group, it.level,
                    it.version?.takeIf(String::isNotBlank), it.groupName?.takeIf(String::isNotBlank),
                    it.skill?.name?.let { name -> SkillNeed(name, it.skill.level) })
            },
            dto.categories.map { RecipeCategory(it.id, it.name.ifEmpty { it.id }) },
        )
    }

    /** The `data` of a `build_recipe` reply: like a craft recipe's, made once, with nothing it makes. */
    fun buildRecipeDetails(data: JsonElement?): RecipeDetails =
        recipeDetails(data).let { it.copy(max = if (it.canCraft) 1 else 0) }

    fun tailorListRequest() = Request("tailor_list", buildJsonObject { })

    fun tailorGarmentRequest(itemId: Long) = Request("tailor_garment", buildJsonObject { put("itemId", itemId) })

    fun tailorMenuRequest(itemId: Long, partId: String) =
        Request("tailor_menu", buildJsonObject { put("itemId", itemId); put("part", partId) })

    @Serializable
    private data class TailorListDto(val garments: List<GarmentSummaryDto> = emptyList(), val kit: SewingKitDto = SewingKitDto(), val tailoring: Int? = null)

    @Serializable
    private data class GarmentSummaryDto(
        val id: Long,
        val name: String = "",
        val icon: String? = null,
        val condition: Float? = null,
        val worn: Boolean = false,
        val holes: Int = 0,
        val patches: Int = 0,
        val repairable: Boolean = true,
    )

    @Serializable
    private data class SewingKitDto(val needle: Boolean = false, val thread: Boolean = false, val fabrics: List<FabricDto> = emptyList())

    @Serializable
    private data class FabricDto(val type: String = "", val name: String = "", val icon: String? = null, val count: Int = 0)

    @Serializable
    private data class GarmentDto(
        val id: Long,
        val name: String = "",
        val icon: String? = null,
        val worn: Boolean = false,
        val condition: Float? = null,
        val blood: Float = 0f,
        val dirt: Float = 0f,
        val cantRepair: String? = null,
        val tailoring: Int? = null,
        val parts: List<GarmentPartDto> = emptyList(),
    )

    @Serializable
    private data class GarmentPartDto(
        val id: String,
        val name: String = "",
        val bite: Float = 0f,
        val scratch: Float = 0f,
        val bullet: Float = 0f,
        val hole: Boolean = false,
        val blood: Float? = null,
        val patch: String? = null,
        val sewing: SewingDto? = null,
    )

    @Serializable
    private data class SewingDto(val name: String = "", val progress: Float = 0f)

    /** The `data` of a `tailor_list` reply. Throws [IllegalArgumentException] if it isn't one. */
    fun tailorList(data: JsonElement?): TailorList {
        requireNotNull(data) { "the game sent no clothes" }
        val dto = json.decodeFromJsonElement<TailorListDto>(lenient(data))
        return TailorList(
            dto.garments.map {
                GarmentSummary(it.id, it.name.ifEmpty { "?" }, it.icon, it.condition, it.worn, it.holes, it.patches,
                    it.repairable)
            },
            SewingKit(dto.kit.needle, dto.kit.thread, dto.kit.fabrics.map { Fabric(it.type, it.name.ifEmpty { it.type }, it.icon, it.count) }),
            dto.tailoring,
        )
    }

    /** The `data` of a `tailor_garment` reply. Throws [IllegalArgumentException] if it isn't one. */
    fun garment(data: JsonElement?): Garment {
        requireNotNull(data) { "the game sent no garment" }
        val dto = json.decodeFromJsonElement<GarmentDto>(lenient(data))
        return Garment(
            dto.id, dto.name.ifEmpty { "?" }, dto.icon, dto.worn, dto.condition, dto.blood, dto.dirt,
            dto.cantRepair?.takeIf(String::isNotBlank), dto.tailoring,
            dto.parts.map { part ->
                GarmentPart(part.id, part.name.ifEmpty { part.id }, part.bite.roundToInt(), part.scratch.roundToInt(), part.bullet.roundToInt(),
                    part.hole, part.blood, part.patch?.takeIf(String::isNotBlank),
                    part.sewing?.let { Sewing(it.name, it.progress) })
            },
        )
    }

    fun setSpeedRequest(speed: GameSpeed) = Request("set_speed", buildJsonObject { put("speed", speed.ordinal) })

    fun deckRunRequest(id: String) = Request("deck_run", buildJsonObject { put("id", id) })

    fun hotbarRequest(slot: Int) = Request("deck_run", buildJsonObject { put("id", "weapons"); put("slot", slot) })

    fun alarmRequest(hour: Int, minute: Int, on: Boolean) = Request("deck_run", buildJsonObject {
        put("id", "alarm"); put("hour", hour); put("minute", minute); put("on", on)
    })

    fun transferRequest(itemId: Long, toContainer: String) = Request("transfer", buildJsonObject {
        put("itemId", JsonPrimitive(itemId))
        put("to", toContainer)
    })

    fun selectContainerRequest(containerId: String) = Request("select_container", buildJsonObject { put("id", containerId) })

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
            "health" -> json.decodeFromJsonElement<HealthDto>(data).let { dto ->
                state.copy(health = Health(female = dto.female, parts = dto.parts.map { part ->
                    BodyPartStatus(part.id, part.name.ifEmpty { part.id }, part.lines.map { line ->
                        HealthLine(line.text, when (line.tone) {
                            "bad" -> HealthTone.BAD
                            "good" -> HealthTone.GOOD
                            "warn" -> HealthTone.WARN
                            else -> HealthTone.NEUTRAL
                        })
                    })
                }))
            }
            "moodles" -> json.decodeFromJsonElement<MoodlesDto>(data).let { dto ->
                state.copy(moodles = Moodles(dto.moodles.map {
                    Moodle(it.id, it.name.ifEmpty { it.id }, it.description, it.level, when (it.tone) {
                        "good" -> MoodleTone.GOOD
                        "bad" -> MoodleTone.BAD
                        else -> MoodleTone.NEUTRAL
                    }, it.color, it.icon)
                }, dto.background, dto.border))
            }
            "here" -> json.decodeFromJsonElement<HereDto>(data).let { dto ->
                state.copy(here = if (!dto.watching) null else HereState(
                    menu = dto.menuId?.let { ItemMenu(it, dto.options.map { option -> option.toDomain() }) },
                    unavailable = dto.unavailable,
                ))
            }
            "time" -> json.decodeFromJsonElement<TimeDto>(data).let {
                // The wire numbers are the game's own (0 pause ... 4 wait), in GameSpeed's order.
                state.copy(time = TimeState(it.speed?.let { speed -> GameSpeed.entries.getOrNull(speed) }, it.canChange, it.gameMenuOpen))
            }
            "deck" -> json.decodeFromJsonElement<DeckDto>(data).let { dto ->
                state.copy(deck = DeckState(
                    clock = dto.clock?.time?.let { DeckClock(it, dto.clock.date, dto.clock.alarm) },
                    // Without an id the app couldn't remember it on the Deck; without a name it can't show it.
                    commands = dto.commands.mapNotNull { c ->
                        if (c.id.isNullOrBlank() || c.name.isNullOrBlank()) null
                        else DeckCommand(c.id, c.name, c.icon, c.available, c.on)
                    },
                    hotbar = dto.hotbar.mapNotNull { s ->
                        s.slot?.let { slot ->
                            HotbarSlot(slot, s.name ?: "Slot $slot",
                                s.item?.let { HotbarItem(it.name ?: "?", it.icon) }, s.inHand)
                        }
                    },
                    alarmClock = dto.alarmClock?.let { a ->
                        if (a.hour == null || a.minute == null) null else AlarmClock(a.name ?: "Watch", a.hour, a.minute, a.on)
                    },
                ))
            }
            // An empty Lua table ({} = not placing) can arrive as [].
            "building" -> if (data !is JsonObject) state.copy(placing = null) else json.decodeFromJsonElement<BuildingDto>(data).let { dto ->
                state.copy(placing = dto.placing?.let { Placing(it.id, it.name.ifEmpty { it.id }, it.icon, it.blocked, it.missing) })
            }
            "map" -> json.decodeFromJsonElement<MapDto>(data).let { dto ->
                if (dto.x == null || dto.y == null) state
                else state.copy(mapPosition = MapPosition(dto.x, dto.y, dto.z, dto.heading, dto.miniMap, dto.worldMap, dto.alwaysShow))
            }
            "explored" -> json.decodeFromJsonElement<ExploredDto>(data).let { dto ->
                val bits = runCatching { inflate(dto.bits) }.getOrNull()
                if (bits == null || dto.width <= 0 || dto.unit <= 0) state
                else state.copy(explored = ExploredAreas(dto.originX, dto.originY, dto.unit, dto.width, dto.height, bits))
            }
            "map_symbols" -> json.decodeFromJsonElement<MapSymbolsDto>(data).let { dto ->
                state.copy(mapSymbols = dto.symbols.mapNotNull {
                    val icon = it.icon?.takeIf { _ -> it.kind == "icon" }
                    val text = it.text?.takeIf { _ -> it.kind == "text" }
                    if (icon == null && text == null) null
                    else MapSymbol(icon, text, it.x, it.y, it.color.takeIf { c -> c.size == 4 } ?: listOf(0f, 0f, 0f, 1f),
                        it.scale, it.rotation, it.anchorX, it.anchorY, it.label, it.minZoom, it.maxZoom)
                })
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
        selected = selected,
    )

    private fun ItemDto.toDomain() = InventoryItem(
        id = id,
        type = type,
        name = name.ifEmpty { type },
        category = category,
        icon = icon,
        weight = weight,
        condition = condition,
        freshness = when (freshness) {
            "fresh" -> Freshness.FRESH
            "stale" -> Freshness.STALE
            "rotten" -> Freshness.ROTTEN
            else -> null
        },
        equipped = when (equipped) {
            "primary" -> EquipSlot.PRIMARY
            "secondary" -> EquipSlot.SECONDARY
            "both" -> EquipSlot.BOTH
            "worn" -> EquipSlot.WORN
            else -> null // absent, or a value from a newer mod: treat as not equipped
        },
        actions = actions.mapNotNull(::itemAction), // unknown (newer) actions are skipped
        shortName = shortName?.takeIf { it.isNotEmpty() && it != name },
        freshnessText = freshnessText,
        cooking = cooking?.let { c ->
            Cooking(
                state = when (c.state) {
                    "cooked" -> CookState.COOKED
                    "uncooked" -> CookState.UNCOOKED
                    "burnt" -> CookState.BURNT
                    else -> null
                },
                text = c.text,
                progress = c.progress?.coerceIn(0f, 1f),
                burning = c.burning,
            )
        },
        read = read,
        unwanted = unwanted,
        fluid = fluid?.takeIf { it.capacity > 0f }?.let { f ->
            FluidFill(f.amount, f.capacity, f.name, f.mixture, f.color?.takeIf { it.size >= 3 }?.let { Triple(it[0], it[1], it[2]) })
        },
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
