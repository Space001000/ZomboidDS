package dev.zomboidds.companion.data

import android.util.Log
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.ConnectionStatus
import dev.zomboidds.companion.domain.Fetched
import dev.zomboidds.companion.domain.GameEvent
import dev.zomboidds.companion.domain.GameGateway
import dev.zomboidds.companion.domain.GameSpeed
import dev.zomboidds.companion.domain.GameState
import dev.zomboidds.companion.domain.ItemCommand
import dev.zomboidds.companion.domain.ItemMenuResult
import dev.zomboidds.companion.domain.BuildList
import dev.zomboidds.companion.domain.Garment
import dev.zomboidds.companion.domain.TailorList
import dev.zomboidds.companion.domain.RecipeDetails
import dev.zomboidds.companion.domain.RecipeList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Talks to the mod's bridge over its WebSocket. Keeps reconnecting: the game not running (yet)
 * is the normal case, not an error.
 */
class WebSocketGameGateway(
    private val client: OkHttpClient,
    /** e.g. http://127.0.0.1:7786 */
    private val bridgeUrl: String,
) : GameGateway {

    private val url = bridgeUrl.replaceFirst("http", "ws") + "/ws"

    private val _state = MutableStateFlow(GameState())
    override val state: StateFlow<GameState> = _state.asStateFlow()

    private val _connection = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Connecting)
    override val connection: StateFlow<ConnectionStatus> = _connection.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    private var job: Job? = null
    private var scope: CoroutineScope? = null

    /** The open connection, if any. */
    @Volatile
    private var socket: WebSocket? = null

    /** Commands sent and waiting for their `command_result`, by id. */
    private val pending = ConcurrentHashMap<String, CompletableDeferred<ProtocolV1.ServerMessage.Reply>>()
    private val commandIds = AtomicLong()

    override fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        this.scope = scope
        job = scope.launch { connectLoop() }
    }

    private suspend fun connectLoop() {
        var failures = 0
        while (coroutineContext.isActive) {
            _connection.value = ConnectionStatus.Connecting
            val closed = CompletableDeferred<String>()
            val socket = client.newWebSocket(Request.Builder().url(url).build(), Listener(closed, onOpen = { failures = 0 }))
            val reason = try {
                closed.await()
            } catch (e: CancellationException) {
                socket.cancel()
                throw e
            } finally {
                this.socket = null
                failPending("Lost the connection to the game")
            }
            val retryIn = BACKOFF_MS[minOf(failures++, BACKOFF_MS.lastIndex)]
            _connection.value = ConnectionStatus.Waiting(reason, retryIn)
            delay(retryIn)
        }
    }

    override fun iconUrl(icon: String) = "$bridgeUrl/icons/$icon.png"

    override suspend fun perform(command: ItemCommand): CommandResult = send(ProtocolV1.request(command)).result

    override suspend fun itemMenu(itemIds: List<Long>): ItemMenuResult = menuFrom(send(ProtocolV1.itemMenuRequest(itemIds)))

    override suspend fun bodyPartMenu(partId: String): ItemMenuResult = menuFrom(send(ProtocolV1.bodyPartMenuRequest(partId)))

    override fun watchHere(on: Boolean) {
        scope?.launch { send(ProtocolV1.watchHereRequest(on)) }
    }

    private fun menuFrom(reply: ProtocolV1.ServerMessage.Reply): ItemMenuResult {
        return when (val result = reply.result) {
            is CommandResult.Failed -> ItemMenuResult.Failed(result.reason)
            CommandResult.Ok -> try {
                ItemMenuResult.Ready(ProtocolV1.itemMenu(reply.data))
            } catch (e: IllegalArgumentException) {
                ItemMenuResult.Failed("The game's menu couldn't be read")
            }
        }
    }

    override suspend fun recipes(): Fetched<RecipeList> =
        fetched(send(ProtocolV1.craftListRequest()), "The game's recipes couldn't be read", ProtocolV1::recipeList)

    override suspend fun recipe(id: String): Fetched<RecipeDetails> =
        fetched(send(ProtocolV1.craftRecipeRequest(id)), "The game's recipe couldn't be read", ProtocolV1::recipeDetails)

    override suspend fun craft(id: String, count: Int): CommandResult = send(ProtocolV1.craftRequest(id, count)).result

    override suspend fun buildRecipes(): Fetched<BuildList> =
        fetched(send(ProtocolV1.buildListRequest()), "The game's build recipes couldn't be read", ProtocolV1::buildList)

    override suspend fun buildRecipe(id: String): Fetched<RecipeDetails> =
        fetched(send(ProtocolV1.buildRecipeRequest(id)), "The game's recipe couldn't be read", ProtocolV1::buildRecipeDetails)

    override suspend fun place(id: String): CommandResult = send(ProtocolV1.buildPlaceRequest(id)).result

    override suspend fun stopPlacing(): CommandResult = send(ProtocolV1.buildStopRequest()).result

    override suspend fun tailorList(): Fetched<TailorList> =
        fetched(send(ProtocolV1.tailorListRequest()), "The game's clothes couldn't be read", ProtocolV1::tailorList)

    override suspend fun garment(itemId: Long): Fetched<Garment> =
        fetched(send(ProtocolV1.tailorGarmentRequest(itemId)), "The garment couldn't be read", ProtocolV1::garment)

    override suspend fun garmentMenu(itemId: Long, partId: String): ItemMenuResult =
        menuFrom(send(ProtocolV1.tailorMenuRequest(itemId, partId)))

    private fun <T> fetched(reply: ProtocolV1.ServerMessage.Reply, unreadable: String, parse: (JsonElement?) -> T): Fetched<T> =
        when (val result = reply.result) {
            is CommandResult.Failed -> Fetched.Failed(result.reason)
            CommandResult.Ok -> try {
                Fetched.Ready(parse(reply.data))
            } catch (e: IllegalArgumentException) {
                Fetched.Failed(unreadable)
            }
        }

    override suspend fun selectMenuOption(menuId: String, optionId: String): CommandResult =
        send(ProtocolV1.menuSelectRequest(menuId, optionId)).result

    override suspend fun setSpeed(speed: GameSpeed): CommandResult = send(ProtocolV1.setSpeedRequest(speed)).result

    override suspend fun runDeckCommand(id: String): CommandResult = send(ProtocolV1.deckRunRequest(id)).result

    override suspend fun drawHotbarSlot(slot: Int): CommandResult = send(ProtocolV1.hotbarRequest(slot)).result

    override suspend fun setAlarm(hour: Int, minute: Int, on: Boolean): CommandResult =
        send(ProtocolV1.alarmRequest(hour, minute, on)).result

    override suspend fun transfer(itemId: Long, toContainer: String): CommandResult =
        send(ProtocolV1.transferRequest(itemId, toContainer)).result

    override suspend fun selectContainer(containerId: String): CommandResult =
        send(ProtocolV1.selectContainerRequest(containerId)).result

    override suspend fun transferAll(fromContainer: String, toContainer: String): CommandResult =
        send(ProtocolV1.transferAllRequest(fromContainer, toContainer)).result

    /** Sends a command and waits for the game's reply; failures come back as a failed reply. */
    private suspend fun send(request: ProtocolV1.Request): ProtocolV1.ServerMessage.Reply {
        fun failed(reason: String) = ProtocolV1.ServerMessage.Reply(null, CommandResult.Failed(reason))
        val ws = socket ?: return failed("Not connected to the game")
        val id = "c-${commandIds.incrementAndGet()}"
        val reply = CompletableDeferred<ProtocolV1.ServerMessage.Reply>()
        pending[id] = reply
        if (!ws.send(ProtocolV1.encode(id, request))) {
            pending.remove(id)
            return failed("Not connected to the game")
        }
        // The game runs commands on its next tick (paused or not); no answer means it's stuck or loading.
        return withTimeoutOrNull(COMMAND_TIMEOUT_MS) { reply.await() }
            ?: failed("The game didn't answer").also { pending.remove(id) }
    }

    private fun failPending(reason: String) {
        pending.keys.toList().forEach { id ->
            pending.remove(id)?.complete(ProtocolV1.ServerMessage.Reply(id, CommandResult.Failed(reason)))
        }
    }

    private inner class Listener(
        private val closed: CompletableDeferred<String>,
        private val onOpen: () -> Unit,
    ) : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            socket = webSocket
            onOpen()
            _connection.value = ConnectionStatus.Connected
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                when (val message = ProtocolV1.decode(text)) {
                    is ProtocolV1.ServerMessage.StateUpdate -> _state.update(message.update)
                    is ProtocolV1.ServerMessage.Reply -> message.id?.let { pending.remove(it) }?.complete(message)
                    is ProtocolV1.ServerMessage.Event -> _events.tryEmit(message.event)
                }
            } catch (e: Exception) {
                // One bad message (e.g. from a newer or broken mod) must not kill the connection.
                Log.w(TAG, "ignoring message: $e", e)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, null)
            closed.complete("closed by the game ($code)")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.i(TAG, "connection lost: $t")
            closed.complete(t.message ?: t.javaClass.simpleName)
        }
    }

    private companion object {
        const val TAG = "ZomboidDS"
        val BACKOFF_MS = longArrayOf(500, 1_000, 2_000, 3_000)
        const val COMMAND_TIMEOUT_MS = 5_000L
    }
}
