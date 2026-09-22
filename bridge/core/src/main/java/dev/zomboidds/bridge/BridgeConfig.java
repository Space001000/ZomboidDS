package dev.zomboidds.bridge;

import dev.zomboidds.bridge.port.GameEnvironment;

/**
 * @param host bind address. Keep this on loopback: anything else exposes game control to the network.
 */
public record BridgeConfig(String host, int port, int commandQueueCapacity) {

    public static final int DEFAULT_PORT = 7786;

    public static BridgeConfig from(GameEnvironment env) {
        return new BridgeConfig(
                env.option("host", "127.0.0.1"),
                Integer.parseInt(env.option("port", String.valueOf(DEFAULT_PORT))),
                Integer.parseInt(env.option("commandQueue", "64")));
    }
}
