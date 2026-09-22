package dev.zomboidds.bridge.adapter.b42;

import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.KahluaTableIterator;
import se.krka.kahlua.vm.Platform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Converts between Kahlua values and plain Java/JSON values. Must be used on the game thread,
 * because Kahlua tables aren't thread-safe.
 *
 * <p>Lua has one table type for both arrays and objects. A table whose keys are exactly
 * {@code 1..n} becomes a {@link List}, an empty table becomes an empty list, and anything else
 * becomes a {@link Map} with string keys.
 */
final class KahluaConverter {

    private static final int MAX_DEPTH = 16;
    private static final double MAX_SAFE_INTEGER = 9007199254740991d; // 2^53 - 1

    private final Supplier<? extends Platform> platform;

    KahluaConverter(Supplier<? extends Platform> platform) {
        this.platform = platform;
    }

    // --- Lua -> Java ---

    Object toJava(Object lua) {
        return toJava(lua, 0);
    }

    private Object toJava(Object value, int depth) {
        if (value == null || value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Double d) {
            return number(d);
        }
        if (value instanceof Number n) {
            return n;
        }
        if (value instanceof KahluaTable table) {
            if (depth >= MAX_DEPTH) {
                throw new IllegalArgumentException("Lua table nested deeper than " + MAX_DEPTH + " (cycle?)");
            }
            return table(table, depth);
        }
        // A Java object leaked into the payload (e.g. an InventoryItem). Stringify it so the bug is
        // visible in the app, instead of failing the whole message.
        return String.valueOf(value);
    }

    private Object table(KahluaTable table, int depth) {
        Map<String, Object> entries = new LinkedHashMap<>();
        boolean sequenceKeys = true;
        KahluaTableIterator it = table.iterator();
        while (it.advance()) {
            Object key = it.getKey();
            sequenceKeys &= key instanceof Double d && d >= 1 && d == Math.rint(d);
            entries.put(key(key), toJava(it.getValue(), depth + 1));
        }
        if (entries.isEmpty()) {
            return List.of();
        }
        if (sequenceKeys && table.len() == entries.size()) {
            List<Object> list = new ArrayList<>(entries.size());
            for (int i = 1; i <= entries.size(); i++) {
                list.add(entries.get(Integer.toString(i)));
            }
            return list;
        }
        return entries;
    }

    private static String key(Object key) {
        if (key instanceof Double d && d == Math.rint(d) && Math.abs(d) <= MAX_SAFE_INTEGER) {
            return Long.toString(d.longValue());
        }
        return String.valueOf(key);
    }

    /** Lua numbers are all doubles; send integral ones as integers. JSON has no NaN/Infinity. */
    private static Object number(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return null;
        }
        if (d == Math.rint(d) && Math.abs(d) <= MAX_SAFE_INTEGER) {
            return (long) d;
        }
        return d;
    }

    // --- Java -> Lua ---

    Object toLua(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof Map<?, ?> map) {
            KahluaTable table = platform.get().newTable();
            map.forEach((k, v) -> {
                if (v != null) {
                    table.rawset(String.valueOf(k), toLua(v));
                }
            });
            return table;
        }
        if (value instanceof List<?> list) {
            KahluaTable table = platform.get().newTable();
            for (int i = 0; i < list.size(); i++) {
                table.rawset((double) (i + 1), toLua(list.get(i)));
            }
            return table;
        }
        return String.valueOf(value);
    }
}
