package dev.zomboidds.bridge.port;

import java.util.Optional;

/** Resolves an icon name (e.g. {@code Item_Axe}) to PNG bytes. Called from HTTP worker threads. */
public interface IconSource {

    Optional<byte[]> loadPng(String iconName);

    /** Does expensive one-time setup (e.g. indexing game files) ahead of the first request. */
    default void warmUp() {
    }
}
