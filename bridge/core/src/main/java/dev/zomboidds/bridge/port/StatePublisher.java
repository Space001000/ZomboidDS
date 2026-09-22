package dev.zomboidds.bridge.port;

/**
 * Publishes messages to all connected clients.
 *
 * <p>Implementations never block on network I/O, so this is safe to call from the game thread.
 * {@code data} must be plain Java values: {@code Map<String, ?>}, {@code List<?>}, {@code String},
 * {@code Number}, {@code Boolean} or {@code null}. Adapters convert engine types (Kahlua tables,
 * ...) before publishing.
 */
public interface StatePublisher {

    void publish(String type, Object data);

    /** Forgets retained state, e.g. when the game returns to the main menu. */
    void clearRetained();
}
