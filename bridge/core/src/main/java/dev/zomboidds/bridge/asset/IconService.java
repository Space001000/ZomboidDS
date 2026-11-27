package dev.zomboidds.bridge.asset;

import dev.zomboidds.bridge.Log;
import dev.zomboidds.bridge.port.IconSource;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Validates icon requests and keeps recently served game icons in memory. */
public final class IconService {

    /** @param permanent whether it's always the same for this game install (mods' icons aren't) */
    public record Icon(byte[] png, boolean permanent) {
    }

    /**
     * A name ({@code Item_Hammer}) or a path of names ({@code Moodles/128/Mood_Sad}); no {@code ..}.
     * Mods name their textures freely, so a name may hold anything but slashes and control characters.
     */
    private static final String NAME = "[^/\\\\\\p{Cntrl}.][^/\\\\\\p{Cntrl}]*";
    private static final Pattern VALID_NAME = Pattern.compile("(?:" + NAME + "/)*" + NAME);
    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_CACHED = 512;

    private final IconSource source;
    private final IconSource modSource;
    private final Map<String, Icon> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Icon> eldest) {
                    return size() > MAX_CACHED;
                }
            });

    public IconService(IconSource source, IconSource modSource) {
        this.source = source;
        this.modSource = modSource;
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

    public Optional<Icon> find(String name) {
        if (name == null || name.length() > MAX_NAME_LENGTH || !VALID_NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        Icon cached = cache.get(name);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<Icon> icon = load(source, name).map(png -> new Icon(png, true));
        if (icon.isPresent()) {
            cache.put(name, icon.get());
            return icon;
        }
        // Not remembered, misses neither: the player may still turn the mod on.
        return load(modSource, name).map(png -> new Icon(png, false));
    }

    private static Optional<byte[]> load(IconSource source, String name) {
        try {
            return source.loadPng(name);
        } catch (RuntimeException e) {
            Log.error("icon source failed for " + name, e);
            return Optional.empty();
        }
    }
}
