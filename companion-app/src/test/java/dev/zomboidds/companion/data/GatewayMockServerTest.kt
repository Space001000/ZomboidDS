package dev.zomboidds.companion.data

import dev.zomboidds.bridge.mock.MockServer
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.ContainerKind
import dev.zomboidds.companion.domain.EquipSlot
import dev.zomboidds.companion.domain.GameSpeed
import dev.zomboidds.companion.domain.ItemAction
import dev.zomboidds.companion.domain.ItemCommand
import dev.zomboidds.companion.domain.ItemMenuResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket

/**
 * End to end without a device: the app's real gateway against the real bridge core, with the mock
 * game (bridge/mock-server) behind it instead of Project Zomboid.
 */
class GatewayMockServerTest {

    private val port = ServerSocket(0).use { it.localPort }
    private lateinit var server: MockServer
    private val scope = CoroutineScope(Dispatchers.IO)
    private val gateway = WebSocketGameGateway(OkHttpClient(), "http://127.0.0.1:$port")

    @Before
    fun setUp() {
        server = MockServer.start(mapOf("port" to port.toString()))
        gateway.start(scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.stop()
    }

    @Test
    fun `receives state and runs commands`() = runBlocking {
        val inventory = withTimeout(5_000) { gateway.state.first { it.inventory != null } }.inventory!!
        val bandage = inventory.items.first { it.name == "Bandage" }
        assertTrue(ItemAction.EQUIP_PRIMARY in bandage.actions)

        assertEquals(CommandResult.Ok, gateway.perform(ItemCommand(bandage.id, ItemAction.EQUIP_PRIMARY)))

        val updated = withTimeout(5_000) {
            gateway.state.first { state -> state.inventory!!.items.first { it.id == bandage.id }.equipped != null }
        }
        val equipped = updated.inventory!!.items.first { it.id == bandage.id }
        assertEquals(EquipSlot.PRIMARY, equipped.equipped)
        assertEquals(listOf(ItemAction.UNEQUIP, ItemAction.DROP), equipped.actions)
    }

    @Test
    fun `the game's item menu can be opened and used`() = runBlocking {
        val inventory = withTimeout(5_000) { gateway.state.first { it.inventory != null } }.inventory!!
        val beans = inventory.items.first { it.name == "Beans" }

        val menu = (gateway.itemMenu(beans.id) as ItemMenuResult.Ready).menu
        val eat = menu.options.first { it.name == "Eat" }
        val all = eat.children.first { it.name == "All" }
        assertTrue(menu.options.any { it.name == "Rename" && !it.enabled && it.tooltip != null })

        assertEquals(CommandResult.Ok, gateway.selectMenuOption(menu.menuId, all.id))
        withTimeout(5_000) { gateway.state.first { state -> state.inventory!!.items.none { it.id == beans.id } } }

        // A menu is single-use, like in the game.
        assertTrue(gateway.selectMenuOption(menu.menuId, all.id) is CommandResult.Failed)
    }

    @Test
    fun `items move between containers`() = runBlocking {
        val containers = withTimeout(5_000) { gateway.state.first { it.containers != null } }.containers!!
        val inventory = containers.first { it.kind == ContainerKind.INVENTORY }
        val shelves = containers.first { it.name == "Shelves" }
        val book = shelves.items!!.first { it.name == "Book" }

        assertEquals(CommandResult.Ok, gateway.transfer(book.id, inventory.id))
        withTimeout(5_000) { gateway.state.first { state -> state.inventory!!.items.any { it.id == book.id } } }

        assertEquals(CommandResult.Ok, gateway.transferAll(shelves.id, containers.first { it.kind == ContainerKind.FLOOR }.id))
        val after = withTimeout(5_000) {
            gateway.state.first { state -> state.containers!!.first { it.id == shelves.id }.items!!.isEmpty() }
        }
        assertEquals(listOf("Pen"), after.containers!!.first { it.kind == ContainerKind.FLOOR }.items!!.map { it.name })

        val crate = containers.first { it.locked }
        assertEquals(CommandResult.Failed("That container is locked"), gateway.transfer(book.id, crate.id))
    }

    @Test
    fun `opening a container selects it in the game`() = runBlocking {
        val containers = withTimeout(5_000) { gateway.state.first { it.containers != null } }.containers!!
        val floor = containers.first { it.kind == ContainerKind.FLOOR }
        assertEquals(CommandResult.Ok, gateway.selectContainer(floor.id))
        withTimeout(5_000) { gateway.state.first { state -> state.containers!!.single { it.selected }.id == floor.id } }
        Unit
    }

    @Test
    fun `items in containers have the game's menu too`() = runBlocking {
        val containers = withTimeout(5_000) { gateway.state.first { it.containers != null } }.containers!!
        val pen = containers.first { it.name == "Shelves" }.items!!.first { it.name == "Pen" }
        assertTrue(gateway.itemMenu(pen.id) is ItemMenuResult.Ready)
    }

    @Test
    fun `Here follows the game while watched`() = runBlocking {
        withTimeout(5_000) { gateway.state.first { it.inventory != null } }
        gateway.watchHere(true)
        val menu = withTimeout(5_000) { gateway.state.first { it.here?.menu != null } }.here!!.menu!!
        val door = menu.options.first { it.name == "Door" }.children.first { it.name == "Open Door" }
        assertEquals(CommandResult.Ok, gateway.selectMenuOption(menu.menuId, door.id))
        // No request: the game sends what's here now by itself.
        withTimeout(5_000) {
            gateway.state.first { state ->
                state.here?.menu?.options?.firstOrNull { it.name == "Door" }?.children?.any { it.name == "Close Door" } == true
            }
        }
        gateway.watchHere(false)
        withTimeout(5_000) { gateway.state.first { it.here == null } }
        Unit
    }

    @Test
    fun `a body part has the game's treatment menu`() = runBlocking {
        val part = withTimeout(5_000) { gateway.state.first { !it.health?.parts.isNullOrEmpty() } }.health!!.parts.first()
        val menu = (gateway.bodyPartMenu(part.id) as ItemMenuResult.Ready).menu
        assertTrue(menu.options.any { it.name == "Apply Bandage" && it.enabled })
        assertTrue(menu.options.any { !it.enabled && it.tooltip != null })
    }

    @Test
    fun `the game speed can be changed`() = runBlocking {
        withTimeout(5_000) { gateway.state.first { it.time?.speed == GameSpeed.PLAY } }
        assertEquals(CommandResult.Ok, gateway.setSpeed(GameSpeed.PAUSED))
        withTimeout(5_000) { gateway.state.first { it.time?.speed == GameSpeed.PAUSED } }
        Unit
    }

    @Test
    fun `failed commands report the game's reason`() = runBlocking {
        withTimeout(5_000) { gateway.state.first { it.inventory != null } }
        val result = gateway.perform(ItemCommand(itemId = 999_999, action = ItemAction.DROP))
        assertEquals(CommandResult.Failed("item not found"), result)
    }
}
