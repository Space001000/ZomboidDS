package dev.zomboidds.companion.data

import dev.zomboidds.companion.domain.HealthLine
import dev.zomboidds.companion.domain.MenuPill
import dev.zomboidds.companion.domain.CookState
import dev.zomboidds.companion.domain.Cooking
import dev.zomboidds.companion.domain.FluidFill
import dev.zomboidds.companion.domain.Freshness
import dev.zomboidds.companion.domain.tileName
import dev.zomboidds.companion.domain.HealthTone
import dev.zomboidds.companion.domain.MoodleTone
import dev.zomboidds.companion.domain.HereState
import dev.zomboidds.companion.domain.GameSpeed
import dev.zomboidds.companion.domain.TimeState
import dev.zomboidds.companion.domain.DeckClock
import dev.zomboidds.companion.domain.DeckCommand
import dev.zomboidds.companion.domain.HotbarSlot
import dev.zomboidds.companion.domain.HotbarItem
import dev.zomboidds.companion.domain.AlarmClock
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.ContainerKind
import dev.zomboidds.companion.domain.GameEvent
import dev.zomboidds.companion.domain.GameState
import dev.zomboidds.companion.domain.ItemAction
import dev.zomboidds.companion.domain.ItemCommand
import dev.zomboidds.companion.domain.MapPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProtocolV1Test {

    /** The shared contract fixtures; unit tests run with the module directory as working directory. */
    private fun fixture(name: String) = File("../protocol/fixtures/$name").readText()

    private fun applyAll(vararg fixtures: String) =
        fixtures.fold(GameState()) { state, name -> ProtocolV1.apply(state, fixture(name)) }

    @Test
    fun `fixtures build up the game state`() {
        val state = applyAll("hello.json", "session.json", "player.json")

        assertEquals("b42", state.bridge?.adapter)
        assertEquals(true, state.session?.inGame)
        assertEquals("42.12", state.session?.gameVersion)
        assertTrue("cmd.equip" in state.session!!.capabilities)
        assertEquals(87.5f, state.player?.health)
        assertEquals(0.34f, state.player?.thirst)
    }

    @Test
    fun `leaving the game clears player data`() {
        val inGame = applyAll("hello.json", "session.json", "player.json")
        val atMenu = ProtocolV1.apply(inGame, """{"v":1,"type":"session","seq":9,"data":{"inGame":false}}""")

        assertFalse(atMenu.session!!.inGame)
        assertNull(atMenu.player)
        assertEquals(inGame.bridge, atMenu.bridge)
    }

    @Test
    fun `hello starts from a clean state`() {
        val state = ProtocolV1.apply(applyAll("hello.json", "session.json", "player.json"), fixture("hello.json"))
        assertNull(state.session)
        assertNull(state.player)
    }

    @Test
    fun `unknown message types are ignored`() {
        val before = applyAll("hello.json")
        assertSame(before, ProtocolV1.apply(before, """{"v":1,"type":"from_the_future","data":{}}"""))
    }

    @Test
    fun `empty Lua tables are accepted where objects or lists are meant`() {
        // Lua sends {} as [] (it can't tell them apart).
        val state = ProtocolV1.apply(GameState(), """{"v":1,"type":"player","data":{"health":50,"stats":[]}}""")
        assertEquals(50f, state.player?.health)
        assertNull(state.player?.hunger)

        val session = ProtocolV1.apply(GameState(), """{"v":1,"type":"session","data":{"inGame":true,"capabilities":[]}}""")
        assertTrue(session.session!!.capabilities.isEmpty())
    }

    @Test
    fun `item actions come from the game side`() {
        val items = applyAll("inventory.json").inventory!!.items
        assertEquals(listOf(ItemAction.UNEQUIP, ItemAction.DROP), items.first { it.name == "Axe" }.actions)
        assertEquals(
            listOf(ItemAction.EQUIP_PRIMARY, ItemAction.EQUIP_SECONDARY, ItemAction.DROP),
            items.first { it.name == "Beans" }.actions,
        )
    }

    @Test
    fun `unknown actions from a newer mod are skipped`() {
        val state = ProtocolV1.apply(GameState(), """{"v":1,"type":"inventory","data":{"items":[
            {"id":1,"name":"Pan","actions":["cook","drop"]}]}}""")
        assertEquals(listOf(ItemAction.DROP), state.inventory!!.items.single().actions)
    }

    @Test
    fun `commands encode like the shared fixture`() {
        val encoded = ProtocolV1.encode("c-17", ItemCommand(10234, ItemAction.EQUIP_PRIMARY))
        val fixture = fixture("command_equip.json")
        fun normalize(json: String) = kotlinx.serialization.json.Json.parseToJsonElement(json)
        assertEquals(normalize(fixture), normalize(encoded))
    }

    @Test
    fun `command results decode as replies`() {
        val reply = ProtocolV1.decode(fixture("command_result.json")) as ProtocolV1.ServerMessage.Reply
        assertEquals("c-17", reply.id)
        assertEquals(CommandResult.Failed("item not found"), reply.result)
    }

    @Test
    fun `vehicle fixtures map to driving and on foot`() {
        val driving = applyAll("vehicle_driving.json").vehicle as dev.zomboidds.companion.domain.Vehicle.Driving
        assertEquals("Chevalier Nyala", driving.name)
        assertEquals(42f, driving.speedKmh)
        assertEquals(0.63f, driving.fuel)
        assertTrue(driving.engineRunning && driving.isDriver)

        assertSame(dev.zomboidds.companion.domain.Vehicle.OnFoot, applyAll("vehicle_driving.json", "vehicle_on_foot.json").vehicle)
    }

    @Test
    fun `item menu replies carry the game's menu`() {
        val reply = ProtocolV1.decode(fixture("item_menu_result.json")) as ProtocolV1.ServerMessage.Reply
        assertEquals(CommandResult.Ok, reply.result)
        val menu = ProtocolV1.itemMenu(reply.data)
        assertEquals("m7", menu.menuId)
        assertEquals(listOf("Read", "Eat", "Rip into sheets", "Drop"), menu.options.map { it.name })
        assertEquals(
            listOf(MenuPill.ACTION, MenuPill.ACTION, null, MenuPill.DROP),
            menu.options.map { it.pill },
        )
        assertEquals(listOf("2.1", "2.2"), menu.options[1].children.map { it.id })
        assertFalse(menu.options[2].enabled)
        assertEquals("Requires a knife", menu.options[2].tooltip)
    }

    @Test
    fun `menu requests encode their arguments`() {
        fun parse(json: String) = kotlinx.serialization.json.Json.parseToJsonElement(json).toString()
        assertEquals(
            parse("""{"v":1,"type":"command","id":"c-1","name":"item_menu","args":{"itemId":42}}"""),
            parse(ProtocolV1.encode("c-1", ProtocolV1.itemMenuRequest(listOf(42)))),
        )
        assertEquals(
            parse("""{"v":1,"type":"command","id":"c-2","name":"item_menu","args":{"itemId":42,"itemIds":[42,43]}}"""),
            parse(ProtocolV1.encode("c-2", ProtocolV1.itemMenuRequest(listOf(42, 43)))),
        )
        assertEquals(
            parse("""{"v":1,"type":"command","id":"c-2","name":"menu_select","args":{"menuId":"m7","optionId":"2.1"}}"""),
            parse(ProtocolV1.encode("c-2", ProtocolV1.menuSelectRequest("m7", "2.1"))),
        )
    }

    @Test
    fun `containers list what the game's windows show`() {
        val containers = applyAll("containers.json").containers!!
        assertEquals(listOf("Inventory", "School Bag", "Shelves", "Crate", "Floor", "Oven"), containers.map { it.name })
        assertEquals(
            listOf(ContainerKind.INVENTORY, ContainerKind.BAG, ContainerKind.NEARBY, ContainerKind.NEARBY, ContainerKind.FLOOR, ContainerKind.NEARBY),
            containers.map { it.kind },
        )
        assertNull("the inventory's items are in the inventory message", containers[0].items)
        assertEquals(listOf("Book", "Pen"), containers[2].items!!.map { it.name })
        assertEquals(50f, containers[2].capacity)
        assertTrue(containers[3].locked)
        assertNull("locked containers can't be looked into", containers[3].items)
        assertEquals(emptyList<Any>(), containers[4].items)
    }

    @Test
    fun `unknown container kinds from a newer mod count as nearby`() {
        val state = ProtocolV1.apply(GameState(),
            """{"v":1,"type":"containers","data":{"containers":[{"id":"c9","kind":"trunk","name":"Trunk"}]}}""")
        assertEquals(ContainerKind.NEARBY, state.containers!!.single().kind)
    }

    @Test
    fun `deck carries the game's clock and commands`() {
        val deck = applyAll("deck.json").deck!!
        assertEquals(DeckClock("14:25", "July 9", "07:00"), deck.clock)
        assertEquals(10, deck.commands.size)
        assertEquals(4, deck.hotbar.size)
        assertEquals(HotbarSlot(1, "Back", HotbarItem("Axe", "Item_Axe"), inHand = true), deck.hotbar[0])
        assertEquals("an empty slot has no item", null, deck.hotbar[3].item)
        assertEquals(AlarmClock("Digital Watch", 7, 0, on = true), deck.alarmClock)
        assertEquals(DeckCommand("search_mode", "Toggle Search Mode", "Search_Icon_Off", available = true, on = true), deck.commands[2])
        assertEquals(null, deck.commands[0].on)
        assertEquals(false, deck.commands.first { it.id == "drop_bag" }.available)

        val noWatch = ProtocolV1.apply(GameState(), """{"v":1,"type":"deck","data":{"commands":[{"id":"map","name":"Map"},{"name":"no id"}]}}""")
        assertEquals("no watch, no clock", null, noWatch.deck!!.clock)
        assertEquals("commands without an id are dropped", listOf("map"), noWatch.deck!!.commands.map { it.id })
    }

    @Test
    fun `time carries the game's speed`() {
        assertEquals(TimeState(GameSpeed.FAST, canChange = true), applyAll("time.json").time)
        val unknown = ProtocolV1.apply(GameState(), """{"v":1,"type":"time","data":{"speed":9}}""")
        assertEquals("a speed from a newer game is unknown, not a crash", TimeState(null, false), unknown.time)
        assertEquals(
            kotlinx.serialization.json.Json.parseToJsonElement("""{"v":1,"type":"command","id":"c-1","name":"set_speed","args":{"speed":0}}"""),
            kotlinx.serialization.json.Json.parseToJsonElement(ProtocolV1.encode("c-1", ProtocolV1.setSpeedRequest(GameSpeed.PAUSED))),
        )
    }

    @Test
    fun `map carries the player's position and the save's map rules`() {
        assertEquals(MapPosition(10745.3f, 9960.7f, 0, 90, miniMapAllowed = true, worldMapAllowed = true), applyAll("map.json").mapPosition)
        val noPosition = ProtocolV1.apply(GameState(), """{"v":1,"type":"map","data":{"miniMap":true}}""")
        assertNull("without a position there's nothing to show", noPosition.mapPosition)
    }

    @Test
    fun `explored unpacks the game's seen-areas bits`() {
        val explored = applyAll("explored.json").explored!!
        assertTrue("unit (0, 0) visited", explored.isUnitSeen(0, 0))
        assertTrue("unit (7, 1) known from a map", explored.isUnitSeen(7, 1))
        assertFalse(explored.isUnitSeen(1, 0))
        assertFalse(explored.isUnitSeen(6, 1))
        assertFalse("outside the world", explored.isUnitSeen(8, 0))
        assertFalse("outside the world", explored.isUnitSeen(-1, 0))
        val broken = ProtocolV1.apply(GameState(), """{"v":1,"type":"explored","data":{"width":8,"height":2,"bits":"not base64!"}}""")
        assertNull("broken data is dropped, not a crash", broken.explored)
    }

    @Test
    fun `map symbols carry the player's icons and notes`() {
        val symbols = applyAll("map_symbols.json").mapSymbols
        assertEquals("a kind from a newer mod is skipped", 2, symbols.size)
        val star = symbols[0]
        assertEquals("LootableMaps/map_star", star.icon)
        assertEquals(listOf(0.8f, 0.1f, 0.1f, 1f), star.color)
        val note = symbols[1]
        assertEquals("Safehouse", note.text)
        assertNull(note.icon)
        assertEquals(15f, note.rotation)
        assertEquals(0f, note.anchorX)
    }

    @Test
    fun `health lists the game's injury lines with their tone`() {
        val parts = applyAll("health.json").health!!.parts
        assertEquals(listOf("Left Hand", "Right Shin"), parts.map { it.name })
        assertEquals(HealthLine("Scratched (Severe)", HealthTone.BAD), parts[0].lines[0])
        assertEquals(HealthTone.GOOD, parts[1].lines[0].tone)
        val empty = ProtocolV1.apply(GameState(), """{"v":1,"type":"health","data":{"parts":[]}}""")
        assertEquals("no injuries is an empty list, not unknown", emptyList<Any>(), empty.health!!.parts)
    }

    @Test
    fun `food carries its freshness, other items none`() {
        val items = applyAll("inventory_full.json").inventory!!.items
        assertEquals(Freshness.STALE, items.first { it.tileName == "Bread" }.freshness)
        assertEquals(Freshness.ROTTEN, items.first { it.tileName == "Banana" }.freshness)
        assertEquals(null, items.first { it.name == "Axe" }.freshness)
    }

    @Test
    fun `food and drinks carry the game's name, cooking and fill`() {
        val items = applyAll("inventory_full.json").inventory!!.items
        val chicken = items.first { it.tileName == "Chicken" }
        assertEquals("Chicken (Fresh, Uncooked)", chicken.name)
        assertEquals("Fresh", chicken.freshnessText)
        assertEquals(Cooking(CookState.UNCOOKED, "Uncooked", progress = 0.62f), chicken.cooking)
        assertEquals(CookState.BURNT, items.first { it.tileName == "Bacon" }.cooking?.state)
        val water = items.first { it.name == "Water Bottle (Water)" }.fluid!!
        assertEquals(0.5f, water.fraction, 0.001f)
        assertEquals("Water", water.name)
        assertEquals(Triple(0.4f, 0.6f, 0.85f), water.color)
        val empty = items.first { it.name == "Empty Water Bottle" }
        assertEquals(0f, empty.fluid!!.fraction, 0f)
        assertNull(empty.fluid!!.name)
        val axe = items.first { it.name == "Axe" }
        assertNull(axe.shortName)
        assertNull(axe.cooking)
        assertNull(axe.fluid)
        assertEquals("Axe", axe.tileName)
        val burning = applyAll("containers.json").containers!!.flatMap { it.items.orEmpty() }.first { it.tileName == "Steak" }.cooking!!
        assertTrue(burning.burning)
        assertEquals(0.35f, burning.progress!!, 0.001f)
    }

    @Test
    fun `read and unwanted items are marked`() {
        val items = applyAll("inventory_full.json").inventory!!.items
        assertTrue(items.first { it.name == "Book" }.read)
        assertTrue(items.first { it.name == "Newspaper" }.unwanted)
        assertFalse(items.first { it.name == "Magazine" }.read)
        assertFalse(items.first { it.name == "Magazine" }.unwanted)
    }

    @Test
    fun `fluid amounts read like the game's tooltip`() {
        val locale = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.US)
        try {
            assertEquals("0.3 / 0.6 L", FluidFill(0.3f, 0.6f).amountText())
            assertEquals("0 / 0.6 L", FluidFill(0f, 0.6f).amountText())
            assertEquals("2.5 / 5 L", FluidFill(2.5f, 5f).amountText())
            assertEquals("12.5 / 20 L", FluidFill(12.5f, 20f).amountText())
        } finally {
            java.util.Locale.setDefault(locale)
        }
    }

    @Test
    fun `crafting replies become the recipe list and a recipe's details`() {
        val list = (ProtocolV1.decode(fixture("craft_list_result.json")) as ProtocolV1.ServerMessage.Reply)
            .let { ProtocolV1.recipeList(it.data) }
        assertEquals(listOf("Rip Clothing", "Crude Stone Axe"), list.recipes.map { it.name })
        assertEquals(listOf(true, false), list.recipes.map { it.canCraft })
        assertEquals("Assembly", list.categories[1].name)
        val axe = (ProtocolV1.decode(fixture("craft_recipe_result.json")) as ProtocolV1.ServerMessage.Reply)
            .let { ProtocolV1.recipeDetails(it.data) }
        assertEquals(listOf(true, false, true), axe.inputs.map { it.ok })
        assertEquals(5, axe.inputs[2].others)
        assertEquals("Stone Axe", axe.outputs.single().name)
        assertEquals(false, axe.skills.single().ok)
        assertEquals(23, axe.seconds)
    }

    @Test
    fun `build replies group a thing's versions`() {
        val list = (ProtocolV1.decode(fixture("build_list_result.json")) as ProtocolV1.ServerMessage.Reply)
            .let { ProtocolV1.buildList(it.data) }
        assertEquals(190, list.recipes.size)
        val chair = list.groups.single { it.key == "Wood_Chair" }
        assertEquals("Wood Chair", chair.name)
        assertEquals(listOf("Shoddy", "Poor", "Good"), chair.versions.map { it.version })
        assertEquals(listOf(1, 3, 6), chair.versions.map { it.skill?.level })
        assertEquals("the best version you can build", "Base.Wood_Chair_Lvl2", chair.preferred.id)
        val crate = list.groups.single { it.key == "Wood_Crate" }
        assertEquals("a version without brackets keeps its name", listOf("Shoddy", null), crate.versions.map { it.version })
        val bench = list.groups.single { it.key == "Base.Log_Bench" }
        assertEquals("one version: its own name", "Log Bench", bench.name)
        assertTrue(list.groups.size < list.recipes.size)

        val door = (ProtocolV1.decode(fixture("build_recipe_result.json")) as ProtocolV1.ServerMessage.Reply)
            .let { ProtocolV1.buildRecipeDetails(it.data) }
        assertTrue("canBuild reads as can make", door.canCraft)
        assertEquals(1, door.max)
        assertTrue(door.inputs[0].keep)
    }

    @Test
    fun `building says what's being placed, and nothing as an empty table`() {
        val placing = applyAll("building.json").placing!!
        assertEquals("Log Bench", placing.name)
        assertTrue(placing.blocked)
        assertEquals(listOf("Log"), placing.missing)
        val placed = GameState(placing = placing)
        assertNull(ProtocolV1.apply(placed, """{"v":1,"type":"building","data":{}}""").placing)
        assertNull("an empty Lua table can come as a list", ProtocolV1.apply(placed, """{"v":1,"type":"building","data":[]}""").placing)
    }

    @Test
    fun `moodles come with the game's texts, colours and images`() {
        val moodles = applyAll("moodles.json").moodles!!
        assertEquals(listOf("Bleeding", "Peckish", "Drowsy"), moodles.list.map { it.name })
        val bleeding = moodles.list[0]
        assertEquals("Moodles/128/Status_Bleeding", bleeding.icon)
        assertEquals(MoodleTone.BAD, bleeding.tone)
        assertEquals(2, bleeding.level)
        assertEquals(listOf(0.608f, 0.392f, 0.392f), bleeding.color)
        assertEquals("Moodles/128/_Moodles_BGsolid", moodles.background)
        val none = ProtocolV1.apply(GameState(), """{"v":1,"type":"moodles","data":{"moodles":[]}}""")
        assertEquals("no moodles is an empty list, not unknown", emptyList<Any>(), none.moodles!!.list)
    }

    @Test
    fun `here carries the world menu while watched`() {
        val here = applyAll("here.json").here!!
        assertEquals("m9", here.menu!!.menuId)
        assertEquals(listOf("Sit on chair", "Open door"), here.menu!!.options.map { it.name })
        val paused = ProtocolV1.apply(GameState(), """{"v":1,"type":"here","data":{"watching":true,"unavailable":"The game is paused"}}""")
        assertEquals(HereState(null, "The game is paused"), paused.here)
        assertNull(ProtocolV1.apply(GameState(), """{"v":1,"type":"here","data":{"watching":false}}""").here)
    }

    @Test
    fun `show asks the app to open a container`() {
        val message = ProtocolV1.decode(fixture("show.json")) as ProtocolV1.ServerMessage.Event
        assertEquals(GameEvent.ShowInventory("c3"), message.event)
        // Events don't change the game state.
        val before = applyAll("hello.json")
        assertSame(before, ProtocolV1.apply(before, fixture("show.json")))
    }

    @Test
    fun `show asks the app to open a garment`() {
        val message = ProtocolV1.decode(fixture("show_garment.json")) as ProtocolV1.ServerMessage.Event
        assertEquals(GameEvent.ShowGarment(30003), message.event)
    }

    @Test
    fun `tailor replies list the clothes and read a garment`() {
        val list = (ProtocolV1.decode(fixture("tailor_list_result.json")) as ProtocolV1.ServerMessage.Reply)
            .let { ProtocolV1.tailorList(it.data) }
        assertEquals(15, list.garments.size)
        val jacket = list.garments[2]
        assertEquals("Leather Jacket", jacket.name)
        assertTrue(jacket.worn)
        assertEquals(2, jacket.holes)
        assertEquals(1, jacket.patches)
        assertEquals(false, list.garments.single { it.name == "Military Boots" }.repairable)
        assertEquals("Military Backpack", list.garments.last().bag)
        assertEquals(listOf("Rag" to 12, "Denim Strips" to 3, "Leather Strips" to 0), list.kit.fabrics.map { it.name to it.count })
        assertTrue(list.kit.needle && list.kit.thread)
        assertEquals(4, list.tailoring)

        val garment = (ProtocolV1.decode(fixture("tailor_garment_result.json")) as ProtocolV1.ServerMessage.Reply)
            .let { ProtocolV1.garment(it.data) }
        assertEquals(30003L, garment.id)
        assertEquals(0.58f, garment.condition)
        assertEquals(null, garment.cantRepair)
        val forearm = garment.parts.single { it.id == "ForeArm_R" }
        assertTrue(forearm.hole)
        assertEquals(0, forearm.bite)
        assertEquals(0.35f, forearm.blood)
        assertEquals("Patch Hole", forearm.sewing?.name)
        assertEquals(forearm.sewing, garment.sewing)
        assertEquals("Leather Strips patch", garment.parts.single { it.id == "Torso_Lower" }.patch)
        assertEquals(40, garment.parts.single { it.id == "Torso_Upper" }.scratch)
    }

    @Test
    fun `transfer requests encode their arguments`() {
        fun parse(json: String) = kotlinx.serialization.json.Json.parseToJsonElement(json).toString()
        assertEquals(
            parse("""{"v":1,"type":"command","id":"c-1","name":"transfer","args":{"itemId":10,"to":"c1"}}"""),
            parse(ProtocolV1.encode("c-1", ProtocolV1.transferRequest(10, "c1"))),
        )
        assertEquals(
            parse("""{"v":1,"type":"command","id":"c-2","name":"transfer_all","args":{"from":"c3","to":"c1"}}"""),
            parse(ProtocolV1.encode("c-2", ProtocolV1.transferAllRequest("c3", "c1"))),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `other protocol versions are rejected`() {
        ProtocolV1.apply(GameState(), """{"v":2,"type":"hello","data":{}}""")
    }
}
