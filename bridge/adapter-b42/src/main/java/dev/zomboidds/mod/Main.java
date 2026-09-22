package dev.zomboidds.mod;

import dev.zomboidds.bridge.Bridge;
import dev.zomboidds.bridge.BridgeConfig;
import dev.zomboidds.bridge.DefaultGameEnvironment;
import dev.zomboidds.bridge.Log;
import dev.zomboidds.bridge.adapter.b42.B42GameAdapter;
import dev.zomboidds.bridge.port.GameEnvironment;

import java.nio.file.Path;

/**
 * Run by ZombieBuddy when the mod loads (see {@code javaPkgName} in mod.info). Starts the bridge
 * server. Must never break the game: failures are logged and the game continues without us.
 */
public final class Main {

    private static Bridge bridge;

    public static synchronized void main(String[] args) {
        if (bridge != null) {
            return; // mods can be reloaded; keep the server that's already running
        }
        try {
            // The game runs with its install directory as working directory.
            GameEnvironment env = DefaultGameEnvironment.fromSystemProperties(Path.of("").toAbsolutePath());
            Log.info("starting bridge, game dir " + env.gameDir());
            bridge = Bridge.start(BridgeConfig.from(env), new B42GameAdapter(), env);
        } catch (Throwable t) {
            Log.error("bridge failed to start; the game continues without it", t);
        }
    }

    private Main() {
    }
}
