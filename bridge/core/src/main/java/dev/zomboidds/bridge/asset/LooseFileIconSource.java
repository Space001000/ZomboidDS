package dev.zomboidds.bridge.asset;

import dev.zomboidds.bridge.Log;
import dev.zomboidds.bridge.port.IconSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** Serves {@code <name>.png} files found anywhere under the given directories. Indexed on first use. */
public final class LooseFileIconSource implements IconSource {

    private final List<Path> roots;
    private Map<String, Path> index;

    public LooseFileIconSource(List<Path> roots) {
        this.roots = List.copyOf(roots);
    }

    @Override
    public Optional<byte[]> loadPng(String iconName) {
        Path file = index().get(iconName);
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

    @Override
    public void warmUp() {
        index();
    }

    private synchronized Map<String, Path> index() {
        if (index == null) {
            Map<String, Path> found = new HashMap<>();
            for (Path root : roots) {
                if (!Files.isDirectory(root)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(root)) {
                    files.filter(p -> p.getFileName().toString().endsWith(".png"))
                            .forEach(p -> {
                                String name = p.getFileName().toString();
                                found.putIfAbsent(name.substring(0, name.length() - 4), p);
                            });
                } catch (IOException e) {
                    Log.warn("could not index " + root + ": " + e.getMessage());
                }
            }
            Log.info("indexed " + found.size() + " loose textures");
            index = found;
        }
        return index;
    }
}
