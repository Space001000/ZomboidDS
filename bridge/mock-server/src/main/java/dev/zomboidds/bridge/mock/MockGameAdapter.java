package dev.zomboidds.bridge.mock;

import dev.zomboidds.bridge.asset.CompositeIconSource;
import dev.zomboidds.bridge.asset.LooseFileIconSource;
import dev.zomboidds.bridge.asset.PackFileIconSource;
import dev.zomboidds.bridge.asset.TileIconSource;
import dev.zomboidds.bridge.port.AdapterInfo;
import dev.zomboidds.bridge.port.BridgeContext;
import dev.zomboidds.bridge.port.GameAdapter;
import dev.zomboidds.bridge.port.GameEnvironment;
import dev.zomboidds.bridge.port.IconSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * A {@link GameAdapter} backed by {@link MockGame} instead of Project Zomboid.
 *
 * <p>Options: {@code gameDir=<Project Zomboid folder>} serves the real item icons from the game's
 * texture packs (otherwise placeholders); {@code inventory=full} starts with a large inventory.
 */
final class MockGameAdapter implements GameAdapter {

    private MockGame game;

    MockGame game() {
        return game;
    }

    @Override
    public AdapterInfo info() {
        return new AdapterInfo("mock", "Fake game for companion app development");
    }

    @Override
    public IconSource createIconSource(GameEnvironment env) {
        String gameDir = env.option("gameDir", null);
        if (gameDir == null) {
            return new PlaceholderIconSource();
        }
        // Real icons where the packs have them, placeholders for the rest.
        return new CompositeIconSource(List.of(
                // Like the real adapter: loose images (moodles, speed buttons) first, then the packs.
                new LooseFileIconSource(List.of(Path.of(gameDir, "media", "textures"), Path.of(gameDir, "media", "ui"))),
                new PackFileIconSource(packs(Path.of(gameDir, "media", "texturepacks"))),
                new TileIconSource(new PackFileIconSource(List.of(Path.of(gameDir, "media", "texturepacks", "Tiles1x.pack")), false)),
                new PlaceholderIconSource()));
    }

    private static List<Path> packs(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().startsWith("UI")
                    && p.getFileName().toString().endsWith(".pack")).sorted().toList();
        } catch (IOException e) {
            throw new IllegalArgumentException("no texture packs in " + dir, e);
        }
    }

    @Override
    public void attach(GameEnvironment env, BridgeContext context) {
        game = new MockGame(context, "full".equals(env.option("inventory", "")) ? "inventory_full.json" : "inventory.json");
        game.start();
    }
}
