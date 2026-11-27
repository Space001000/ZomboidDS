package dev.zomboidds.bridge.adapter.b42;

import dev.zomboidds.bridge.asset.ModIconSource;
import zombie.GameWindow;
import zombie.ZomboidFileSystem;
import zombie.fileSystem.FileSystem;

import java.io.File;
import java.util.List;
import java.util.Map;

/** The active mods' files and texture packs, as the game has loaded them. */
final class GameMods implements ModIconSource.Mods {

    private String gameMedia;

    @Override
    public Object state() {
        return List.of(List.copyOf(ZomboidFileSystem.instance.getModIDs()), files().size());
    }

    @Override
    public Map<String, String> files() {
        return ZomboidFileSystem.instance.activeFileMap;
    }

    @Override
    public String packOf(String texture) {
        FileSystem.SubTexture found = GameWindow.texturePackTextures.get(texture);
        return found == null ? null : found.packName;
    }

    @Override
    public boolean isGameFile(String path) {
        if (gameMedia == null) {
            // Only its media folder: mods can be in the game's own mods folder too.
            gameMedia = new File(ZomboidFileSystem.instance.base.canonicalFile, "media").getPath() + File.separator;
        }
        return path.startsWith(gameMedia);
    }
}
