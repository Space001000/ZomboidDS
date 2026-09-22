package dev.zomboidds.bridge.mock;

import dev.zomboidds.bridge.Bridge;
import dev.zomboidds.bridge.BridgeConfig;
import dev.zomboidds.bridge.DefaultGameEnvironment;
import dev.zomboidds.bridge.port.GameEnvironment;

import java.nio.file.Path;
import java.util.Map;

/** Starts the real bridge with the fake game behind it. Used by {@link MockServerMain} and by tests. */
public final class MockServer {

    private final Bridge bridge;
    private final MockGameAdapter adapter;

    private MockServer(Bridge bridge, MockGameAdapter adapter) {
        this.bridge = bridge;
        this.adapter = adapter;
    }

    public static MockServer start(Map<String, String> options) throws Exception {
        System.setProperty("java.awt.headless", "true");
        GameEnvironment env = new DefaultGameEnvironment(Path.of("."), options);
        MockGameAdapter adapter = new MockGameAdapter();
        return new MockServer(Bridge.start(BridgeConfig.from(env), adapter, env), adapter);
    }

    /** Blocks, reading keyboard commands for the fake game (see {@link MockGame#runConsole()}). */
    public void runConsole() throws Exception {
        adapter.game().runConsole();
    }

    public void stop() {
        bridge.stop();
    }
}
