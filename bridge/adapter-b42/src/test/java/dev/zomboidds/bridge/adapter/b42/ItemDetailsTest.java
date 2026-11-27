package dev.zomboidds.bridge.adapter.b42;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ItemDetailsTest {

    // The game's default highlight colours: bad red, good green.
    private static Double tone(float r, float g, float b) {
        return ItemDetails.tone(r, g, b, 1f, 0.2f, 0.2f, 0.2f, 1f, 0.2f);
    }

    @Test
    void barColoursBecomeTheirPlaceBetweenBadAndGood() {
        assertEquals(0.0, tone(1f, 0.2f, 0.2f));
        assertEquals(1.0, tone(0.2f, 1f, 0.2f));
        assertEquals(0.25, tone(0.8f, 0.4f, 0.2f));
    }

    @Test
    void plainTextIsNeutralAndRedWarningsAreBad() {
        assertNull(tone(1f, 1f, 0.8f));
        assertNull(tone(1f, 1f, 1f));
        assertEquals(0.0, tone(1f, 0.1f, 0.1f));
        assertEquals(0.0, tone(1f, 0.5f, 0.5f));
    }

    @Test
    void labelsLoseTheirColonAndExtraSpaces() {
        assertEquals("Encumbrance", ItemDetails.clean("Encumbrance:"));
        assertEquals("2.4 (3.0 unequipped)", ItemDetails.clean("2.4    (3.0 unequipped)"));
        assertEquals("Remaining", ItemDetails.clean("Remaining: "));
        assertEquals("", ItemDetails.clean(null));
    }
}
