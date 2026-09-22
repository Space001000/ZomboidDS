package dev.zomboidds.companion.data

import android.util.Log
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.ConnectionStatus
import dev.zomboidds.companion.domain.GameGateway
import dev.zomboidds.companion.domain.GameState
import dev.zomboidds.companion.domain.ItemCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

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

    private var job: Job? = null

    /** The open connection, if any. */
    @Volatile
    private var socket: WebSocket? = null

    /** Commands sent and waiting for their `command_result`, by id. */
    private val pending = ConcurrentHashMap<String, CompletableDeferred<CommandResult>>()
    private val commandIds = AtomicLong()

    override fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
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

    override suspend fun perform(command: ItemCommand): CommandResult {
        val ws = socket ?: return CommandResult.Failed("Not connected to the game")
        val id = "c-${commandIds.incrementAndGet()}"
        val reply = CompletableDeferred<CommandResult>()
        pending[id] = reply
        if (!ws.send(ProtocolV1.encode(id, command))) {
            pending.remove(id)
            return CommandResult.Failed("Not connected to the game")
        }
        // The game runs commands on its next tick; a paused game doesn't tick.
        return withTimeoutOrNull(COMMAND_TIMEOUT_MS) { reply.await() }
            ?: CommandResult.Failed("The game didn't answer (is it paused?)").also { pending.remove(id) }
    }

    private fun failPending(reason: String) {
        pending.keys.toList().forEach { id -> pending.remove(id)?.complete(CommandResult.Failed(reason)) }
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
                    is ProtocolV1.ServerMessage.Reply -> message.id?.let { pending.remove(it) }?.complete(message.result)
                }
            } catch (e: IllegalArgumentException) {
                // One bad message (e.g. from a newer or broken mod) must not kill the connection.
                Log.w(TAG, "ignoring message: ${e.message}")
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, null)
            closed.complete("closed by the game ($code)")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            closed.complete(t.message ?: t.javaClass.simpleName)
        }
    }

    private companion object {
        const val TAG = "ZomboidDS"
        val BACKOFF_MS = longArrayOf(500, 1_000, 2_000, 3_000)
        const val COMMAND_TIMEOUT_MS = 5_000L
    }
}
