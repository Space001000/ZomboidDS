package dev.zomboidds.bridge.asset;

import dev.zomboidds.bridge.port.IconSource;

import java.util.Optional;

/**
 * Icons the game cuts out of world object sprites at runtime ({@code texture:splitIcon()}, named
 * {@code <sprite>_Icon}, e.g. {@code fixtures_windows_01_17_Icon} for a window in a menu). They aren't
 * files, so we serve the object's sprite itself from the tile packs.
 */
public final class TileIconSource implements IconSource {

    static final String SUFFIX = "_Icon";

    private final IconSource sprites;

    /** @param sprites world tile sprites by name, e.g. a {@link PackFileIconSource} over Tiles1x.pack */
    public TileIconSource(IconSource sprites) {
        this.sprites = sprites;
    }

    @Override
    public Optional<byte[]> loadPng(String iconName) {
        if (!iconName.endsWith(SUFFIX) || iconName.length() == SUFFIX.length()) {
            return Optional.empty();
        }
        return sprites.loadPng(iconName.substring(0, iconName.length() - SUFFIX.length()));
    }

    @Override
    public void warmUp() {
        sprites.warmUp();
    }
}
