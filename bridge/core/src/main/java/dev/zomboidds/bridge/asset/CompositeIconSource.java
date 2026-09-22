package dev.zomboidds.bridge.asset;

import dev.zomboidds.bridge.port.IconSource;

import java.util.List;
import java.util.Optional;

/** Asks each source in order and returns the first hit. */
public final class CompositeIconSource implements IconSource {

    private final List<IconSource> sources;

    public CompositeIconSource(List<IconSource> sources) {
        this.sources = List.copyOf(sources);
    }

    @Override
    public Optional<byte[]> loadPng(String iconName) {
        for (IconSource source : sources) {
            Optional<byte[]> png = source.loadPng(iconName);
            if (png.isPresent()) {
                return png;
            }
        }
        return Optional.empty();
    }

    @Override
    public void warmUp() {
        sources.forEach(IconSource::warmUp);
    }
}
