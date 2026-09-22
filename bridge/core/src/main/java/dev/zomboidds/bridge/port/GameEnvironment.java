package dev.zomboidds.bridge.port;

import java.nio.file.Path;

/** What an adapter may know about the process it runs in. */
public interface GameEnvironment {

    /** Game install directory (the one containing {@code media/}). */
    Path gameDir();

    /** A setting, e.g. {@code port}. See {@link dev.zomboidds.bridge.DefaultGameEnvironment} for where they come from. */
    String option(String key, String defaultValue);
}
