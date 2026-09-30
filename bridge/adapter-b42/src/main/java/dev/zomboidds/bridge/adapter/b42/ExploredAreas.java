package dev.zomboidds.bridge.adapter.b42;

import dev.zomboidds.bridge.Log;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.Deflater;
import zombie.iso.IsoMetaGrid;
import zombie.iso.IsoWorld;
import zombie.worldMap.WorldMapVisited;

/**
 * The parts of the world the player has seen, as the game's map remembers them
 * ({@code zombie.worldMap.WorldMapVisited}, 42.20): one 2-bit entry per 32x32-tile unit, bit 1
 * "visited" (walked or driven near), bit 2 "known" (from a map the player read, or the sandbox
 * option that makes the whole map known). The minimap shows a unit when either is set.
 *
 * <p>Layout, from the game's {@code getFlags}: {@code width = cellsWide * 8} units per row,
 * 4 units per byte, unit {@code x} in bits {@code (x % 4) * 2} of byte {@code x / 4 + y * width / 4};
 * unit (0, 0) starts at tile {@code (metaGrid.minX * 256, metaGrid.minY * 256)}.
 *
 * <p>The game keeps the bytes in a package-private field, read here by reflection; only ever
 * copied, never written. Called on the game thread.
 */
final class ExploredAreas {

    static final int TILES_PER_UNIT = 32;
    private static final int UNITS_PER_CELL = 8;
    private static final int TILES_PER_CELL = 256;
    private static final long CHECK_EVERY_MS = 2_000;

    private Field visitedField;
    private boolean broken;
    private byte[] lastSent;
    private long nextCheck;

    /** The seen areas if they changed since the last call that returned them, at most every 2 s. */
    Map<String, Object> changed(long now) {
        if (broken || now < nextCheck) {
            return null;
        }
        nextCheck = now + CHECK_EVERY_MS;
        try {
            IsoMetaGrid grid = IsoWorld.instance != null ? IsoWorld.instance.getMetaGrid() : null;
            if (grid == null) {
                return null;
            }
            byte[] bits = read(WorldMapVisited.getInstance());
            if (bits == null || Arrays.equals(bits, lastSent)) {
                return null;
            }
            int width = (grid.getMaxX() - grid.getMinX() + 1) * UNITS_PER_CELL;
            lastSent = bits;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("originX", grid.getMinX() * TILES_PER_CELL);
            data.put("originY", grid.getMinY() * TILES_PER_CELL);
            data.put("unit", TILES_PER_UNIT);
            data.put("width", width);
            data.put("height", bits.length / (width / 4));
            data.put("bits", encode(bits));
            return data;
        } catch (ReflectiveOperationException | RuntimeException e) {
            // A game update renamed something: no seen areas, the rest of the map still works.
            broken = true;
            Log.error("can't read the map's seen areas", e);
            return null;
        }
    }

    /** Forget what was sent, e.g. when a new game starts or a client needs everything again. */
    void reset() {
        lastSent = null;
        nextCheck = 0;
    }

    private byte[] read(WorldMapVisited visited) throws ReflectiveOperationException {
        if (visited == null) {
            return null;
        }
        if (visitedField == null) {
            visitedField = WorldMapVisited.class.getDeclaredField("visited");
            visitedField.setAccessible(true);
        }
        byte[] bits = (byte[]) visitedField.get(visited);
        return bits != null ? bits.clone() : null;
    }

    /** Deflate (zlib), then base64. Mostly zeros, so a whole map is a few KB. */
    static String encode(byte[] bits) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(bits);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }
}
