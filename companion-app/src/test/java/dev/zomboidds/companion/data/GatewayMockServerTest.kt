package dev.zomboidds.companion.data

import dev.zomboidds.bridge.mock.MockServer
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.EquipSlot
import dev.zomboidds.companion.domain.ItemAction
import dev.zomboidds.companion.domain.ItemCommand
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
    fun `failed commands report the game's reason`() = runBlocking {
        withTimeout(5_000) { gateway.state.first { it.inventory != null } }
        val result = gateway.perform(ItemCommand(itemId = 999_999, action = ItemAction.DROP))
        assertEquals(CommandResult.Failed("item not found"), result)
    }
}
