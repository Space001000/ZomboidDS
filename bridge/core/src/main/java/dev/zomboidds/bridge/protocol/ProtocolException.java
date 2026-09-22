package dev.zomboidds.bridge.protocol;

public class ProtocolException extends Exception {

    /** Command id if it could be read, so the error can still be reported back. May be null. */
    private final String commandId;

    public ProtocolException(String message, String commandId) {
        super(message);
        this.commandId = commandId;
    }

    public String commandId() {
        return commandId;
    }
}
