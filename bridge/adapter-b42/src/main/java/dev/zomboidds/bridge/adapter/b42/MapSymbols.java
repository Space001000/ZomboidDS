package dev.zomboidds.bridge.adapter.b42;

import dev.zomboidds.bridge.Log;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import zombie.Lua.LuaManager;
import zombie.ZomboidFileSystem;
import zombie.core.Translator;
import zombie.inventory.types.MapItem;
import zombie.worldMap.symbols.MapSymbolDefinitions;
import zombie.worldMap.symbols.WorldMapBaseSymbol;
import zombie.worldMap.symbols.WorldMapSymbols;
import zombie.worldMap.symbols.WorldMapTextSymbol;
import zombie.worldMap.symbols.WorldMapTextureSymbol;

/**
 * What the game's minimap shows with its Symbols option on: the game's printed labels (town, river,
 * building names; {@link MapLabels}) and the symbols and notes the player put on their world map
 * ({@code MapItem.getSingleton()}, the map the game's world map and minimap show; 42.20). From the
 * symbol list only the player's own ({@code userDefined}): the labels are read from their files.
 *
 * <p>Position, colour and scale are package-private fields of {@code WorldMapBaseSymbol}, read by
 * reflection; only ever read. Called on the game thread.
 */
final class MapSymbols {

    private static final long CHECK_EVERY_MS = 2_000;

    private final Map<String, Field> fields = new LinkedHashMap<>();
    private boolean broken;
    private List<Map<String, Object>> lastSent;
    private List<Map<String, Object>> labels;
    private long nextCheck;

    /** The player's symbols if they changed since the last call that returned them, at most every 2 s. */
    Map<String, Object> changed(long now) {
        if (broken || now < nextCheck) {
            return null;
        }
        nextCheck = now + CHECK_EVERY_MS;
        try {
            MapItem map = MapItem.getSingleton();
            WorldMapSymbols symbols = map != null ? map.getSymbols() : null;
            if (symbols == null) {
                return null;
            }
            if (labels == null) {
                labels = readLabels();
            }
            List<Map<String, Object>> list = new ArrayList<>(labels);
            for (int i = 0; i < symbols.getSymbolCount(); i++) {
                Map<String, Object> symbol = describe(symbols.getSymbolByIndex(i));
                if (symbol != null) {
                    list.add(symbol);
                }
            }
            if (list.equals(lastSent)) {
                return null;
            }
            lastSent = list;
            return Map.of("symbols", list);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            Log.error("can't read the map's symbols", e);
            return null;
        }
    }

    void reset() {
        lastSent = null;
        labels = null;
        nextCheck = 0;
    }

    private Map<String, Object> describe(WorldMapBaseSymbol symbol) throws ReflectiveOperationException {
        if (symbol == null || !symbol.isUserDefined() || !bool(symbol, "visible")) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        if (symbol instanceof WorldMapTextureSymbol texture) {
            String icon = iconName(texture.getSymbolID());
            if (icon == null) {
                return null;
            }
            out.put("kind", "icon");
            out.put("icon", icon);
        } else if (symbol instanceof WorldMapTextSymbol text) {
            String shown = text.getTranslatedText();
            if (shown == null || shown.isBlank()) {
                return null;
            }
            out.put("kind", "text");
            out.put("text", shown);
        } else {
            return null;
        }
        out.put("x", round(number(symbol, "x")));
        out.put("y", round(number(symbol, "y")));
        out.put("color", List.of(round(number(symbol, "r")), round(number(symbol, "g")), round(number(symbol, "b")), round(number(symbol, "a"))));
        out.put("scale", round(number(symbol, "scale")));
        out.put("rotation", round(symbol.getRotation()));
        out.put("anchorX", round(symbol.getAnchorX()));
        out.put("anchorY", round(symbol.getAnchorY()));
        out.put("minZoom", round(symbol.getMinZoom()));
        out.put("maxZoom", round(symbol.getMaxZoom()));
        return out;
    }

    /**
     * Every map's labels, like the game's MapUtils.initDefaultAnnotations: the map folders in use
     * (mods first), resolved through the game's file system so mod maps are found too.
     */
    private static List<Map<String, Object>> readLabels() {
        List<Map<String, Object>> all = new ArrayList<>();
        for (String dir : LuaManager.GlobalObject.getLotDirectories()) {
            try {
                String path = ZomboidFileSystem.instance.getString("media/maps/" + dir + "/worldmap-annotations.lua");
                Path file = path != null ? Path.of(path) : null;
                if (file != null && Files.isRegularFile(file)) {
                    all.addAll(MapLabels.parse(Files.readString(file), key -> Translator.getText(key), MapSymbols::iconName));
                }
            } catch (IOException | RuntimeException e) {
                Log.error("can't read the map labels of " + dir, e);
            }
        }
        return all;
    }

    /** "media/ui/LootableMaps/map_star.png" as the bridge's icon endpoint knows it: "LootableMaps/map_star". */
    private static String iconName(String symbolId) {
        MapSymbolDefinitions.MapSymbolDefinition definition = symbolId != null
                ? MapSymbolDefinitions.getInstance().getSymbolById(symbolId) : null;
        String path = definition != null ? definition.getTexturePath() : null;
        if (path == null) {
            return null;
        }
        path = path.replace('\\', '/');
        for (String root : List.of("media/ui/", "media/textures/")) {
            if (path.startsWith(root)) {
                path = path.substring(root.length());
            }
        }
        return path.endsWith(".png") ? path.substring(0, path.length() - 4) : path;
    }

    private float number(WorldMapBaseSymbol symbol, String name) throws ReflectiveOperationException {
        return field(name).getFloat(symbol);
    }

    private boolean bool(WorldMapBaseSymbol symbol, String name) throws ReflectiveOperationException {
        return field(name).getBoolean(symbol);
    }

    private Field field(String name) throws ReflectiveOperationException {
        Field field = fields.get(name);
        if (field == null) {
            field = WorldMapBaseSymbol.class.getDeclaredField(name);
            field.setAccessible(true);
            fields.put(name, field);
        }
        return field;
    }

    private static double round(float value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
