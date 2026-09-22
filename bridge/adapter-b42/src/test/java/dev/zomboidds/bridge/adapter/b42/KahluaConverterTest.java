package dev.zomboidds.bridge.adapter.b42;

import org.junit.jupiter.api.Test;
import se.krka.kahlua.j2se.J2SEPlatform;
import se.krka.kahlua.vm.KahluaTable;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Runs against the game's own Kahlua implementation (from projectzomboid.jar). */
class KahluaConverterTest {

    private final J2SEPlatform platform = J2SEPlatform.getInstance();
    private final KahluaConverter converter = new KahluaConverter(() -> platform);

    @Test
    void sequenceTableBecomesList() {
        KahluaTable items = table(1d, "a", 2d, "b");
        assertEquals(List.of("a", "b"), converter.toJava(items));
    }

    @Test
    void mixedKeysBecomeMapWithStringKeys() {
        KahluaTable t = table("name", "Axe", 1d, "x");
        assertEquals(Map.of("name", "Axe", "1", "x"), converter.toJava(t));
    }

    @Test
    void integralDoublesBecomeLongsAndNaNBecomesNull() {
        Map<?, ?> map = (Map<?, ?>) converter.toJava(table("id", 10234d, "weight", 0.5d, "bad", Double.NaN));
        assertEquals(10234L, map.get("id"));
        assertEquals(0.5d, map.get("weight"));
        assertNull(map.get("bad"));
    }

    @Test
    void emptyTableBecomesEmptyList() {
        assertEquals(List.of(), converter.toJava(platform.newTable()));
    }

    @Test
    void javaValuesBecomeLuaTables() {
        Object lua = converter.toLua(Map.of("args", Map.of("itemId", 7L), "list", List.of("x")));
        KahluaTable table = assertInstanceOf(KahluaTable.class, lua);
        assertEquals(7.0d, ((KahluaTable) table.rawget("args")).rawget("itemId"));
        assertEquals("x", ((KahluaTable) table.rawget("list")).rawget(1));
        assertEquals(1, ((KahluaTable) table.rawget("list")).len());
    }

    private KahluaTable table(Object... keysAndValues) {
        KahluaTable t = platform.newTable();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            t.rawset(keysAndValues[i], keysAndValues[i + 1]);
        }
        return t;
    }
}
