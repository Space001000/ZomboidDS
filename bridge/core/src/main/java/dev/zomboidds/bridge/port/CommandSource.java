package dev.zomboidds.bridge.port;

import dev.zomboidds.bridge.protocol.Command;

import java.util.List;

public interface CommandSource {

    /** Removes and returns up to {@code max} pending commands, oldest first. Never blocks. */
    List<Command> drain(int max);
}
