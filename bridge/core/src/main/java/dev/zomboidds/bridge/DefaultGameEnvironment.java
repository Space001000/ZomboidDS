package dev.zomboidds.bridge;

import dev.zomboidds.bridge.port.GameEnvironment;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class DefaultGameEnvironment implements GameEnvironment {

    private static final String PROPERTY_PREFIX = "zomboidds.";

    private final Path gameDir;
    private final Map<String, String> options;

    public DefaultGameEnvironment(Path gameDir, Map<String, String> options) {
        this.gameDir = gameDir;
        this.options = Map.copyOf(options);
    }

    /**
     * Inside the game: options come from JVM system properties such as {@code -Dzomboidds.port=7787},
     * which users can add in Zomdroid's JVM-args setting. Nobody should need them normally.
     */
    public static DefaultGameEnvironment fromSystemProperties(Path gameDir) {
        Map<String, String> options = new HashMap<>();
        System.getProperties().forEach((key, value) -> {
            String name = key.toString();
            if (name.startsWith(PROPERTY_PREFIX)) {
                options.put(name.substring(PROPERTY_PREFIX.length()), value.toString());
            }
        });
        return new DefaultGameEnvironment(gameDir, options);
    }

    /** Parses {@code key=value,key=value} (used by the mock server's command line). */
    public static Map<String, String> parseOptions(String args) {
        Map<String, String> result = new HashMap<>();
        if (args == null || args.isBlank()) {
            return result;
        }
        for (String pair : args.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                result.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return result;
    }

    @Override
    public Path gameDir() {
        return gameDir;
    }

    @Override
    public String option(String key, String defaultValue) {
        return options.getOrDefault(key, defaultValue);
    }
}
