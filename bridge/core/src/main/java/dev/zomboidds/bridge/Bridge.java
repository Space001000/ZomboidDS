package dev.zomboidds.bridge;

import dev.zomboidds.bridge.asset.IconService;
import dev.zomboidds.bridge.command.CommandInbox;
import dev.zomboidds.bridge.port.BridgeContext;
import dev.zomboidds.bridge.port.CommandSource;
import dev.zomboidds.bridge.port.GameAdapter;
import dev.zomboidds.bridge.port.GameEnvironment;
import dev.zomboidds.bridge.port.StatePublisher;
import dev.zomboidds.bridge.protocol.Command;
import dev.zomboidds.bridge.protocol.JsonCodec;
import dev.zomboidds.bridge.protocol.Protocol;
import dev.zomboidds.bridge.protocol.ProtocolException;
import dev.zomboidds.bridge.server.BridgeServer;
import dev.zomboidds.bridge.state.ClientConnection;
import dev.zomboidds.bridge.state.StateHub;

import java.util.LinkedHashMap;
import java.util.Map;

/** Composition root: wires the server, state hub and command inbox to one {@link GameAdapter}. */
public final class Bridge implements BridgeContext, BridgeServer.Listener {

    private final GameAdapter adapter;
    private final JsonCodec codec = new JsonCodec();
    private final CommandInbox inbox;
    private final IconService icons;
    private final BridgeServer server;
    private final StateHub hub;

    private Bridge(BridgeConfig config, GameAdapter adapter, GameEnvironment env) {
        this.adapter = adapter;
        this.inbox = new CommandInbox(config.commandQueueCapacity());
        this.icons = new IconService(adapter.createIconSource(env));
        this.server = new BridgeServer(config.host(), config.port(), icons);
        this.hub = new StateHub(codec, this::helloData);
        server.setListener(this);
    }

    /**
     * Attaches the adapter to the game and starts serving. Throws if either step fails; the caller
     * decides whether that's fatal (it never should be for the game itself).
     */
    public static Bridge start(BridgeConfig config, GameAdapter adapter, GameEnvironment env) throws Exception {
        Bridge bridge = new Bridge(config, adapter, env);
        adapter.attach(env, bridge);
        bridge.server.startDaemon();
        bridge.icons.warmUpInBackground();
        Log.info("bridge " + Protocol.BRIDGE_VERSION + " running with adapter '" + adapter.info().id() + "'");
        return bridge;
    }

    public void stop() {
        server.stop();
        hub.shutdown();
    }

    // --- BridgeContext (what the adapter sees) ---

    @Override
    public StatePublisher state() {
        return hub;
    }

    @Override
    public CommandSource commands() {
        return inbox;
    }

    @Override
    public int connectedClients() {
        return server.clients().size();
    }

    // --- BridgeServer.Listener (network threads) ---

    @Override
    public void onConnected(ClientConnection client) {
        hub.onClientConnected(client);
    }

    @Override
    public void onText(ClientConnection client, String text) {
        try {
            Command command = codec.decodeCommand(text);
            if (!inbox.offer(command)) {
                reject(client, command.id(), "command queue full (is the game running?)");
            }
        } catch (ProtocolException e) {
            reject(client, e.commandId(), e.getMessage());
        }
    }

    @Override
    public void onDisconnected(ClientConnection client) {
        hub.onClientDisconnected(client);
    }

    private void reject(ClientConnection client, String commandId, String error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", commandId);
        result.put("ok", false);
        result.put("error", error);
        hub.sendTo(client, Protocol.TYPE_COMMAND_RESULT, result);
    }

    private Object helloData() {
        Map<String, Object> hello = new LinkedHashMap<>();
        hello.put("protocol", Protocol.VERSION);
        hello.put("bridge", Protocol.BRIDGE_VERSION);
        hello.put("adapter", adapter.info().id());
        return hello;
    }
}
