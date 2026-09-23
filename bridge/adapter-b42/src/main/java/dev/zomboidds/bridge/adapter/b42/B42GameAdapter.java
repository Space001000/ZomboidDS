package dev.zomboidds.bridge.adapter.b42;

import dev.zomboidds.bridge.Log;
import dev.zomboidds.bridge.asset.CompositeIconSource;
import dev.zomboidds.bridge.asset.LooseFileIconSource;
import dev.zomboidds.bridge.asset.PackFileIconSource;
import dev.zomboidds.bridge.asset.TileIconSource;
import dev.zomboidds.bridge.port.AdapterInfo;
import dev.zomboidds.bridge.port.BridgeContext;
import dev.zomboidds.bridge.port.GameAdapter;
import dev.zomboidds.bridge.port.GameEnvironment;
import dev.zomboidds.bridge.port.IconSource;
import zombie.Lua.LuaManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Project Zomboid Build 42. Loaded through ZombieBuddy, which also exposes our Lua API
 * ({@link dev.zomboidds.mod.ZomboidDSBridge}), so this adapter doesn't hook the game itself.
 */
public final class B42GameAdapter implements GameAdapter {

    /** Texture packs that only hold world tiles. Skipping them keeps icon indexing fast. */
    private static final List<String> SKIPPED_PACK_PREFIXES = List.of("Tiles", "Jumbo");

    @Override
    public AdapterInfo info() {
        return new AdapterInfo("b42", "Project Zomboid Build 42");
    }

    @Override
    public IconSource createIconSource(GameEnvironment env) {
        Path media = env.gameDir().resolve("media");
        return new CompositeIconSource(List.of(
                new LooseFileIconSource(List.of(media.resolve("textures"), media.resolve("ui"))),
                new PackFileIconSource(itemPacks(media.resolve("texturepacks"))),
                // World objects in menus ("Here"): their sprite from the small tile set (43 MB,
                // header-only indexing; the 2x sets are far bigger and not needed for icons).
                new TileIconSource(new PackFileIconSource(List.of(media.resolve("texturepacks").resolve("Tiles1x.pack")), false))));
    }

    @Override
    public void attach(GameEnvironment env, BridgeContext context) {
        // LuaManager.platform is read on each use: it only exists once the game has set up Lua.
        LuaApi.install(new LuaApi(context, new KahluaConverter(() -> LuaManager.platform)));
    }

    private static List<Path> itemPacks(Path dir) {
        if (!Files.isDirectory(dir)) {
            Log.warn("no texturepacks directory at " + dir.toAbsolutePath());
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".pack"))
                    .filter(p -> SKIPPED_PACK_PREFIXES.stream()
                            .noneMatch(prefix -> p.getFileName().toString().startsWith(prefix)))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            Log.warn("could not list " + dir + ": " + e.getMessage());
            return List.of();
        }
    }
}
