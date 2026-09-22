package dev.zomboidds.bridge.state;

/** A connected companion app, as seen by {@link StateHub}. Only called from the hub's sender thread. */
public interface ClientConnection {

    void send(String json);

    String describe();
}
