package dev.zomboidds.bridge.adapter.b42;

import dev.zomboidds.bridge.Log;
import dev.zomboidds.bridge.port.BridgeContext;
import dev.zomboidds.bridge.protocol.Command;
import se.krka.kahlua.vm.KahluaTable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What the Lua mod can do with the bridge. Called on the game thread (Lua runs there) through
 * {@link dev.zomboidds.mod.ZomboidDSBridge}; never blocks.
 */
public final class LuaApi {

    private static volatile LuaApi current;

    private final BridgeContext bridge;
    private final KahluaConverter converter;
    /** Map work that doesn't need the game (compressing, reading files), so it doesn't hold up a frame. */
    private final ExecutorService background = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ZomboidDS-map");
        t.setDaemon(true);
        return t;
    });
    /** Counts {@link #reset}s, so a message prepared in the background for an earlier game is dropped. */
    private final AtomicLong session = new AtomicLong();
    /** The last message {@link #emitIfChanged} sent per type; game thread only. */
    private final Map<String, Object> lastSent = new HashMap<>();
    /** What {@link #sameAsLast} was last given per key; game thread only. */
    private final Map<String, Object> lastCompared = new HashMap<>();
    private final ExploredAreas explored = new ExploredAreas();
    private final MapSymbols symbols = new MapSymbols(background);

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

    /**
     * Like {@link #emit}, but only if the message differs from the last one this sent for
     * {@code type}. The mod's snapshots are compared here, after converting, because walking the
     * table in Lua to compare it took as long as building it (~4 ms for a full inventory on the Thor).
     *
     * @return whether it was sent
     */
    public boolean emitIfChanged(String type, Object data) {
        try {
            Object converted = converter.toJava(data);
            if (lastSent.containsKey(type) && Objects.equals(converted, lastSent.get(type))) {
                return false;
            }
            lastSent.put(type, converted);
            bridge.state().publish(type, converted);
            return true;
        } catch (RuntimeException e) {
            Log.error("emit('" + type + "') failed", e);
            return false;
        }
    }

    /**
     * Whether {@code data} equals what was last passed for {@code key}; remembers it either way. For
     * the mod's own "did this change" checks that don't send anything (e.g. keeping Here's menu id),
     * compared here for the same reason as {@link #emitIfChanged}.
     */
    public boolean sameAsLast(String key, Object data) {
        try {
            Object converted = converter.toJava(data);
            boolean same = lastCompared.containsKey(key) && Objects.equals(converted, lastCompared.get(key));
            lastCompared.put(key, converted);
            return same;
        } catch (RuntimeException e) {
            Log.error("sameAsLast('" + key + "') failed", e);
            lastCompared.remove(key);
            return false;
        }
    }

    /** Forget what {@link #emitIfChanged} sent, so everything goes out again (e.g. a client connected). */
    public void forgetSent() {
        lastSent.clear();
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

    /**
     * Sends the parts of the map the player has seen as {@code explored}, if they changed (checked
     * at most every 2 s). Retained like any state, so apps that connect later get it too. Only the
     * copy is made here; it's compressed and sent from the background thread.
     */
    public void publishExplored() {
        ExploredAreas.Seen seen = explored.changed(System.currentTimeMillis());
        if (seen == null) {
            return;
        }
        long current = session.get();
        background.execute(() -> {
            try {
                Map<String, Object> data = seen.message();
                synchronized (session) { // not between reset's count and its clearing
                    if (session.get() == current) {
                        bridge.state().publish("explored", data);
                    }
                }
            } catch (RuntimeException e) {
                Log.error("can't send the map's seen areas", e);
            }
        });
    }

    /** Sends the symbols and notes the player put on their map as {@code map_symbols}, if they changed (every 2 s at most). */
    public void publishSymbols() {
        Map<String, Object> data = symbols.changed(System.currentTimeMillis());
        if (data != null) {
            bridge.state().publish("map_symbols", data);
        }
    }

    public int clients() {
        return bridge.connectedClients();
    }

    /**
     * Called by the Lua mod whenever its code is (re)loaded, i.e. at startup and when entering or
     * leaving a game: state from the previous session is stale.
     */
    public void reset() {
        synchronized (session) {
            session.incrementAndGet();
            bridge.state().clearRetained();
        }
        lastSent.clear();
        lastCompared.clear();
        explored.reset();
        symbols.reset();
        bridge.state().publish("session", Map.of("inGame", false));
        bridge.commands().drain(Integer.MAX_VALUE);
    }
}
