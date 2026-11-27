package dev.zomboidds.bridge.asset;

import dev.zomboidds.bridge.Log;
import dev.zomboidds.bridge.port.IconSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Icons from the player's active mods, found through what the game itself has loaded: textures in a
 * mod's texture packs, or single images in its {@code media/textures} and {@code media/ui}.
 *
 * <p>Mods load after the bridge has started, and the game loads them again when a save uses other
 * mods, so the images are indexed again once {@link Mods#state()} changes.
 */
public final class ModIconSource implements IconSource {

    /** What the game has loaded. Read from HTTP worker threads, while the game may be changing it. */
    public interface Mods {

        /** Changes (by {@code equals}) when {@link #files()} does. */
        Object state();

        /** Every file the game knows: lower-case path ({@code media/textures/item_axe.png}) to full path. */
        Map<String, String> files();

        /** The name of the texture pack the game has this texture from, or null. */
        String packOf(String texture);

        /** Whether the file is the game's, not a mod's. */
        boolean isGameFile(String path);
    }

    private static final String TEXTURES = "media/textures/";
    private static final String UI = "media/ui/";
    private static final String PNG = ".png";

    private final Mods mods;
    private final Map<Path, PackFileIconSource> packs = new HashMap<>();
    private PackFileIconSource lastPack;
    private Object indexedState;
    private Map<String, Path> images = Map.of();

    public ModIconSource(Mods mods) {
        this.mods = mods;
    }

    @Override
    public synchronized Optional<byte[]> loadPng(String iconName) {
        refresh();
        Optional<byte[]> fromPack = fromPack(iconName); // the game looks in its packs first
        if (fromPack.isPresent()) {
            return fromPack;
        }
        Path file = images.get(iconName.toLowerCase(Locale.ENGLISH));
        if (file == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException e) {
            Log.warn("could not read " + file + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<byte[]> fromPack(String iconName) {
        String packName = mods.packOf(iconName);
        if (packName == null) {
            return Optional.empty();
        }
        String path = mods.files().get("media/texturepacks/" + packName.toLowerCase(Locale.ENGLISH) + ".pack");
        if (path == null || mods.isGameFile(path)) {
            return Optional.empty();
        }
        PackFileIconSource pack = packs.computeIfAbsent(Path.of(path), p -> new PackFileIconSource(List.of(p)));
        if (lastPack != pack) {
            if (lastPack != null) {
                lastPack.releasePage(); // one decoded page in memory, however many mods have packs
            }
            lastPack = pack;
        }
        return pack.loadPng(iconName);
    }

    private void refresh() {
        Object state = mods.state();
        if (state.equals(indexedState)) {
            return;
        }
        Map<String, Path> found = new HashMap<>();
        mods.files().forEach((name, path) -> {
            if (!name.endsWith(PNG) || !(name.startsWith(TEXTURES) || name.startsWith(UI)) || mods.isGameFile(path)) {
                return;
            }
            String fileName = name.substring(name.lastIndexOf('/') + 1, name.length() - PNG.length());
            // Item icons sit directly in media/textures; a deeper file of the same name is e.g. a model's.
            if (name.equals(TEXTURES + fileName + PNG)) {
                found.put(fileName, Path.of(path));
            } else {
                found.putIfAbsent(fileName, Path.of(path));
            }
        });
        images = found;
        packs.clear();
        lastPack = null;
        indexedState = state;
        Log.info("indexed " + found.size() + " textures from mods");
    }
}
