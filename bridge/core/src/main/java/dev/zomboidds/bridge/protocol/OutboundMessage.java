package dev.zomboidds.bridge.protocol;

public record OutboundMessage(String type, long seq, Object data) {
}
