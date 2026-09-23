package dev.zomboidds.bridge.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LooseFileIconSourceTest {

    @TempDir
    Path ui;

    private void png(String path, int marker) throws IOException {
        Path file = ui.resolve(path);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {(byte) marker});
    }

    @Test
    void iconsAreFoundByFileNameOrByPathWhenSizesShareAName() throws IOException {
        png("Item_Hammer.png", 1);
        png("Moodles/32/Mood_Sad.png", 32);
        png("Moodles/128/Mood_Sad.png", (byte) 128);
        LooseFileIconSource icons = new LooseFileIconSource(List.of(ui));

        assertArrayEquals(new byte[] {1}, icons.loadPng("Item_Hammer").orElseThrow());
        assertArrayEquals(new byte[] {32}, icons.loadPng("Moodles/32/Mood_Sad").orElseThrow());
        assertArrayEquals(new byte[] {(byte) 128}, icons.loadPng("Moodles/128/Mood_Sad").orElseThrow());
        assertTrue(icons.loadPng("Moodles/64/Mood_Sad").isEmpty());
    }

    @Test
    void theServiceAcceptsPathsButNothingThatLeavesTheFolders() {
        List<String> asked = new ArrayList<>();
        IconService service = new IconService(name -> {
            asked.add(name);
            return Optional.of(new byte[] {1});
        });

        assertTrue(service.find("Moodles/128/Mood_Sad").isPresent());
        assertTrue(service.find("Item_Hammer").isPresent());
        for (String bad : new String[] {"../secret", "Moodles/../../x", "/etc/passwd", "a//b", "Moodles/", "a\\b", "x".repeat(129)}) {
            assertTrue(service.find(bad).isEmpty(), bad);
        }
        assertEquals(List.of("Moodles/128/Mood_Sad", "Item_Hammer"), asked);
    }
}
