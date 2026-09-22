package dev.zomboidds.bridge.port;

/**
 * The seam between the version-neutral bridge and one specific game build.
 *
 * <p>Everything that depends on game internals (Kahlua details, asset layout, ...) lives behind
 * this interface. Each game build gets its own implementation, shipped in its own jar inside the
 * mod's version folder (e.g. {@code 42/media/java/client/}), so the game's mod loader picks the
 * right one. The core and the protocol stay the same.
 */
public interface GameAdapter {

    AdapterInfo info();

    /** Where item icons come from in this build. */
    IconSource createIconSource(GameEnvironment env);

    /**
     * Connects the adapter to the bridge. Everything the adapter needs from the bridge comes
     * through {@code context}.
     */
    void attach(GameEnvironment env, BridgeContext context) throws Exception;
}
