package dev.zomboidds.bridge.port;

/** @param id short id reported to clients in {@code hello}, e.g. {@code "b42"} */
public record AdapterInfo(String id, String description) {
}
