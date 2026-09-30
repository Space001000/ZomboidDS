package dev.zomboidds.bridge.adapter.b42;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapLabelsTest {

    /** Made-up labels in the shape of the game's generated worldmap-annotations.lua. */
    private static final String FILE = """
            return function(mapUI)
            	local mapAPI = mapUI.javaObject:getAPIv3()
            	local symbolsAPI = mapAPI:getSymbolsAPIv2()
            	local symbol
            	symbol = symbolsAPI:addUntranslatedText("MapLabel_Creek", "text-water-nofade", 1200, 340.5)
            	symbol:setRGBA(0.000, 0.000, 0.000, 0.000)
            	symbol:setScale(1.5)
            	symbol:setAnchor(0.50, 0.25)
            	symbol:setRotation(90.2)
            	symbol:setMatchPerspective(true)
            	symbol:setMinZoom(0.00)
            	symbol:setMaxZoom(13.00)
            	symbol:setUserDefined(false)

            	symbol = symbolsAPI:addTranslatedText("OLD<br>MILL", "text-building", 1500, 600)
            	symbol:setMinZoom(13.50)

            	symbol = symbolsAPI:addTexture("Star", 10, 20)
            	symbol:setRGBA(1.000, 0.000, 0.000, 1.000)

            	symbol = symbolsAPI:addTexture("NoSuchSymbol", 10, 20)
            	symbol:setScale(2.0)

            	symbol = symbolsAPI:addTranslatedText("Hidden", "text-place", 1, 2)
            	symbol:setVisible(false)
            end
            """;

    @Test
    void readsTheLabelsTheGameWouldAdd() {
        List<Map<String, Object>> labels = MapLabels.parse(FILE,
                key -> key.equals("MapLabel_Creek") ? "Creek" : key,
                id -> id.equals("Star") ? "LootableMaps/map_star" : null);

        assertEquals(3, labels.size(), "unknown symbols and hidden labels are left out");

        Map<String, Object> creek = labels.get(0);
        assertEquals("text", creek.get("kind"));
        assertEquals("Creek", creek.get("text"), "untranslated text is a translation key");
        assertEquals(1200.0, creek.get("x"));
        assertEquals(340.5, creek.get("y"));
        assertEquals(1.5, creek.get("scale"));
        assertEquals(0.25, creek.get("anchorY"));
        assertEquals(90.2, creek.get("rotation"));
        assertEquals(13.0, creek.get("maxZoom"));
        assertEquals(List.of(0.0, 0.0, 0.0, 0.0), creek.get("color"), "no colour of its own: the map style's");
        assertEquals(true, creek.get("label"));

        Map<String, Object> mill = labels.get(1);
        assertEquals("OLD\nMILL", mill.get("text"), "translated text as it is, <br> as a line break");
        assertEquals(13.5, mill.get("minZoom"));
        assertEquals(24.0, mill.get("maxZoom"), "the game's defaults for what the file doesn't set");

        assertEquals("LootableMaps/map_star", labels.get(2).get("icon"));
        assertEquals(List.of(1.0, 0.0, 0.0, 1.0), labels.get(2).get("color"));
    }
}
