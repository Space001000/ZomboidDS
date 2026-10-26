package dev.zomboidds.mod;

import dev.zomboidds.bridge.adapter.b42.LuaApi;
import dev.zomboidds.bridge.protocol.Protocol;
import me.zed_0xff.zombie_buddy.Exposer;
import se.krka.kahlua.vm.KahluaTable;

/**
 * The {@code ZomboidDSBridge} global in Lua (exposed by ZombieBuddy). Call with a dot:
 * <pre>
 *   ZomboidDSBridge.emit("player", data)   -- publish state or events to the companion app
 *   ZomboidDSBridge.emitIfChanged("player", data) -- the same, unless equal to the last one sent this way
 *   ZomboidDSBridge.forgetSent()           -- send everything again on the next emitIfChanged
 *   ZomboidDSBridge.sameAsLast("k", data)  -- whether data equals what was last passed for "k"
 *   ZomboidDSBridge.poll(8)                -- array of { id, name, args } commands, or nil
 *   ZomboidDSBridge.publishExplored()      -- send the map areas the player has seen, if changed
 *   ZomboidDSBridge.publishSymbols()       -- send the player's map symbols and notes, if changed
 *   ZomboidDSBridge.clients()              -- number of connected companion apps
 *   ZomboidDSBridge.clockMs()              -- milliseconds with fractions, for timing
 *   ZomboidDSBridge.gcCount()              -- garbage collections so far, for timing (-1: unknown)
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

    /** Publishes unless equal to the last message of this type sent this way; returns whether it was sent. */
    public static boolean emitIfChanged(String type, Object data) {
        LuaApi api = LuaApi.current();
        return api != null && api.emitIfChanged(type, data);
    }

    /** Whether data equals what was last passed for key (remembered either way); false without a bridge. */
    public static boolean sameAsLast(String key, Object data) {
        LuaApi api = LuaApi.current();
        return api != null && api.sameAsLast(key, data);
    }

    public static void forgetSent() {
        LuaApi api = LuaApi.current();
        if (api != null) {
            api.forgetSent();
        }
    }

    public static KahluaTable poll(int max) {
        LuaApi api = LuaApi.current();
        return api != null ? api.poll(max) : null;
    }

    public static void publishExplored() {
        LuaApi api = LuaApi.current();
        if (api != null) {
            api.publishExplored();
        }
    }

    public static void publishSymbols() {
        LuaApi api = LuaApi.current();
        if (api != null) {
            api.publishSymbols();
        }
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

    /** Milliseconds with fractions, for timing the mod's work (the game's getTimestampMs has whole ones). */
    public static double clockMs() {
        return System.nanoTime() / 1_000_000.0;
    }

    private static volatile boolean gcUnavailable;

    /**
     * Garbage collections so far (all collectors), so the mod's timings can tell the game's GC
     * pauses from its own work; -1 if the JVM doesn't say.
     */
    public static double gcCount() {
        if (gcUnavailable) {
            return -1;
        }
        try {
            return GcCounter.count();
        } catch (Throwable e) { // e.g. no java.management in the game's runtime
            gcUnavailable = true;
            return -1;
        }
    }

    /** Separate, so the bridge still loads where java.lang.management is missing. */
    private static final class GcCounter {
        private static final java.util.List<java.lang.management.GarbageCollectorMXBean> COLLECTORS =
                java.lang.management.ManagementFactory.getGarbageCollectorMXBeans();

        static long count() {
            long total = 0;
            for (java.lang.management.GarbageCollectorMXBean collector : COLLECTORS) {
                total += Math.max(0, collector.getCollectionCount());
            }
            return total;
        }
    }

    public static String version() {
        return Protocol.BRIDGE_VERSION;
    }

    private ZomboidDSBridge() {
    }
}
