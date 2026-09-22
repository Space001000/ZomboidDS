package dev.zomboidds.mod;

import dev.zomboidds.bridge.adapter.b42.LuaApi;
import dev.zomboidds.bridge.protocol.Protocol;
import me.zed_0xff.zombie_buddy.Exposer;
import se.krka.kahlua.vm.KahluaTable;

/**
 * The {@code ZomboidDSBridge} global in Lua (exposed by ZombieBuddy). Call with a dot:
 * <pre>
 *   ZomboidDSBridge.emit("player", data)   -- publish state or events to the companion app
 *   ZomboidDSBridge.poll(8)                -- array of { id, name, args } commands, or nil
 *   ZomboidDSBridge.clients()              -- number of connected companion apps
 *   ZomboidDSBridge.reset()                -- forget state from the previous session
 *   ZomboidDSBridge.version()              -- bridge version string
 * </pre>
 * All calls are safe no-ops if the bridge failed to start.
 */
@Exposer.LuaClass
public final class ZomboidDSBridge {

    public static void emit(String type, Object data) {
        LuaApi api = LuaApi.current();
        if (api != null) {
            api.emit(type, data);
        }
    }

    public static KahluaTable poll(int max) {
        LuaApi api = LuaApi.current();
        return api != null ? api.poll(max) : null;
    }

    public static int clients() {
        LuaApi api = LuaApi.current();
        return api != null ? api.clients() : 0;
    }

    public static void reset() {
        LuaApi api = LuaApi.current();
        if (api != null) {
            api.reset();
        }
    }

    public static String version() {
        return Protocol.BRIDGE_VERSION;
    }

    private ZomboidDSBridge() {
    }
}
