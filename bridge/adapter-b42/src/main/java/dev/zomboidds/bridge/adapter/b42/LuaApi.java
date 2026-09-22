package dev.zomboidds.bridge.adapter.b42;

import dev.zomboidds.bridge.Log;
import dev.zomboidds.bridge.port.BridgeContext;
import dev.zomboidds.bridge.protocol.Command;
import se.krka.kahlua.vm.KahluaTable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the Lua mod can do with the bridge. Called on the game thread (Lua runs there) through
 * {@link dev.zomboidds.mod.ZomboidDSBridge}; never blocks.
 */
public final class LuaApi {

    private static volatile LuaApi current;

    private final BridgeContext bridge;
    private final KahluaConverter converter;

    LuaApi(BridgeContext bridge, KahluaConverter converter) {
        this.bridge = bridge;
        this.converter = converter;
    }

    static void install(LuaApi api) {
        current = api;
    }

    /** Null until the bridge has started (or if it failed to). */
    public static LuaApi current() {
        return current;
    }

    public void emit(String type, Object data) {
        try {
            bridge.state().publish(type, converter.toJava(data));
        } catch (RuntimeException e) {
            Log.error("emit('" + type + "') failed", e);
        }
    }

    /** Up to {@code max} commands as a Lua array of { id, name, args }, or null if there are none. */
    public KahluaTable poll(int max) {
        List<Command> commands = bridge.commands().drain(Math.max(1, max));
        if (commands.isEmpty()) {
            return null;
        }
        List<Object> batch = new ArrayList<>(commands.size());
        for (Command c : commands) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", c.id());
            entry.put("name", c.name());
            entry.put("args", c.args());
            batch.add(entry);
        }
        return (KahluaTable) converter.toLua(batch);
    }

    public int clients() {
        return bridge.connectedClients();
    }

    /**
     * Called by the Lua mod whenever its code is (re)loaded, i.e. at startup and when entering or
     * leaving a game: state from the previous session is stale.
     */
    public void reset() {
        bridge.state().clearRetained();
        bridge.state().publish("session", Map.of("inGame", false));
        bridge.commands().drain(Integer.MAX_VALUE);
    }
}
