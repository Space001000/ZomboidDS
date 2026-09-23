package dev.zomboidds.bridge.protocol;

import java.util.Set;

/** Constants from {@code protocol/PROTOCOL.md}. */
public final class Protocol {

    public static final int VERSION = 1;
    public static final String BRIDGE_VERSION = "0.18.1";

    public static final String TYPE_HELLO = "hello";
    public static final String TYPE_SESSION = "session";
    public static final String TYPE_COMMAND = "command";
    public static final String TYPE_COMMAND_RESULT = "command_result";
    public static final String TYPE_SHOW = "show";

    /** Event types are delivered once and never replayed to newly connected clients. */
    public static final Set<String> EVENT_TYPES = Set.of(TYPE_COMMAND_RESULT, TYPE_SHOW);

    public static boolean isRetained(String type) {
        return !EVENT_TYPES.contains(type);
    }

    private Protocol() {
    }
}
