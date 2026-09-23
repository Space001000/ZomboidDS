package dev.zomboidds.bridge.asset;

import dev.zomboidds.bridge.port.IconSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileIconSourceTest {

    private final List<String> asked = new ArrayList<>();
    private final IconSource sprites = name -> {
        asked.add(name);
        return name.equals("fixtures_windows_01_17") ? Optional.of(new byte[] {1}) : Optional.empty();
    };

    @Test
    void menuIconsCutFromASpriteAreServedFromTheSprite() {
        TileIconSource tiles = new TileIconSource(sprites);
        assertTrue(tiles.loadPng("fixtures_windows_01_17_Icon").isPresent());
        assertEquals(List.of("fixtures_windows_01_17"), asked);
    }

    @Test
    void worldObjectIconsAreCutToTheirVisiblePixels() {
        java.awt.image.BufferedImage sprite = new java.awt.image.BufferedImage(10, 20, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        sprite.setRGB(0, 0, 0x10FFFFFF);           // a faint shadow pixel: ignored
        for (int y = 15; y < 18; y++) {
            for (int x = 2; x < 6; x++) {
                sprite.setRGB(x, y, 0xFF808080);   // the object itself
            }
        }
        java.awt.image.BufferedImage icon = PackFileIconSource.visiblePart(sprite);
        assertEquals(4, icon.getWidth());
        assertEquals(3, icon.getHeight());
    }

    @Test
    void otherNamesAreLeftToTheOtherSources() {
        TileIconSource tiles = new TileIconSource(sprites);
        assertTrue(tiles.loadPng("Item_Hammer").isEmpty());
        assertTrue(tiles.loadPng("_Icon").isEmpty());
        assertTrue(asked.isEmpty());
    }
}
