package dev.zomboidds.bridge.protocol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A command sent by the companion app. {@code args} holds plain JSON values (may contain nulls). */
public record Command(String id, String name, Map<String, Object> args) {

    public Command {
        args = args == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(args));
    }
}
