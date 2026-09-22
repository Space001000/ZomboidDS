package dev.zomboidds.bridge.asset;

import dev.zomboidds.bridge.Log;
import dev.zomboidds.bridge.port.IconSource;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Validates icon requests and keeps recently served icons (and misses) in memory. */
public final class IconService {

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_.\\-]{1,128}");
    private static final int MAX_CACHED = 512;

    private final IconSource source;
    private final Map<String, Optional<byte[]>> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Optional<byte[]>> eldest) {
                    return size() > MAX_CACHED;
                }
            });

    public IconService(IconSource source) {
        this.source = source;
    }

    /** Prepares the icon source in the background so the first requests are fast. */
    public void warmUpInBackground() {
        Thread thread = new Thread(() -> {
            long start = System.currentTimeMillis();
            try {
                source.warmUp();
                Log.info("icons ready in " + (System.currentTimeMillis() - start) + " ms");
            } catch (RuntimeException e) {
                Log.error("icon warm-up failed (icons are loaded on demand instead)", e);
            }
        }, "ZomboidDS-icons");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY); // don't compete with the game for CPU
        thread.start();
    }

    public Optional<byte[]> find(String name) {
        if (name == null || !VALID_NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        Optional<byte[]> cached = cache.get(name);
        if (cached != null) {
            return cached;
        }
        Optional<byte[]> loaded;
        try {
            loaded = source.loadPng(name);
        } catch (RuntimeException e) {
            Log.error("icon source failed for " + name, e);
            loaded = Optional.empty();
        }
        cache.put(name, loaded);
        return loaded;
    }
}
