package dev.zomboidds.bridge.port;

/** What the bridge offers an adapter. */
public interface BridgeContext {

    /** Outbound: game state for the companion app. Safe to call from the game thread. */
    StatePublisher state();

    /** Inbound: commands from the companion app, to be drained on the game thread. */
    CommandSource commands();

    int connectedClients();
}
