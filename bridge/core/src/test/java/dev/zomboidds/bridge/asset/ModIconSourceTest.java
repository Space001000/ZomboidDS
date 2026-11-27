package dev.zomboidds.bridge.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModIconSourceTest {

    @TempDir
    Path dir;

    private final Map<String, String> files = new HashMap<>();

    private final ModIconSource icons = new ModIconSource(new ModIconSource.Mods() {
        @Override
        public Object state() {
            return files.size();
        }

        @Override
        public Map<String, String> files() {
            return files;
        }

        @Override
        public String packOf(String texture) {
            return null;
        }

        @Override
        public boolean isGameFile(String path) {
            return path.startsWith(dir.resolve("game").toString());
        }
    });

    private void file(String owner, String path, int marker) throws IOException {
        Path file = dir.resolve(owner).resolve(path);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {(byte) marker});
        files.put(path.toLowerCase(Locale.ENGLISH), file.toString());
    }

    @Test
    void findsImagesOfModsThatLoadLaterWhateverTheCase() throws IOException {
        assertTrue(icons.loadPng("Item_M16A1").isEmpty());
        file("mod", "media/textures/item_M16A1.png", 1);
        assertArrayEquals(new byte[] {1}, icons.loadPng("Item_M16A1").orElseThrow());
        files.clear();
        assertTrue(icons.loadPng("Item_M16A1").isEmpty());
    }

    @Test
    void anItemsIconWinsFromADeeperTextureAndTheGamesFilesAreNotIndexed() throws IOException {
        file("mod", "media/textures/weapons/Item_M16.png", 1);
        file("mod", "media/textures/Item_M16.png", 2);
        file("mod", "media/ui/Other.png", 3);
        file("game", "media/textures/Item_Hammer.png", 4);
        assertArrayEquals(new byte[] {2}, icons.loadPng("Item_M16").orElseThrow());
        assertArrayEquals(new byte[] {3}, icons.loadPng("Other").orElseThrow());
        assertTrue(icons.loadPng("Item_Hammer").isEmpty());
    }

    @Test
    void theServiceKeepsTheGamesIconsButNeitherModsIconsNorMisses() {
        Map<String, byte[]> modIcons = new HashMap<>();
        int[] asked = {0};
        IconService service = new IconService(
                name -> {
                    asked[0]++;
                    return name.equals("Item_Axe") ? Optional.of(new byte[] {1}) : Optional.empty();
                },
                name -> Optional.ofNullable(modIcons.get(name)));

        assertTrue(service.find("Item_Axe").orElseThrow().permanent());
        service.find("Item_Axe");
        assertEquals(1, asked[0]);

        assertTrue(service.find("Item_My gun (v2)").isEmpty());
        modIcons.put("Item_My gun (v2)", new byte[] {2});
        assertFalse(service.find("Item_My gun (v2)").orElseThrow().permanent());
        modIcons.clear();
        assertTrue(service.find("Item_My gun (v2)").isEmpty());
    }
}
