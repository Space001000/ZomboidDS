package dev.zomboidds.bridge.command;

import dev.zomboidds.bridge.port.CommandSource;
import dev.zomboidds.bridge.protocol.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded hand-off from network threads (producers) to the game thread (consumer). */
public final class CommandInbox implements CommandSource {

    private final int capacity;
    private final Queue<Command> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger();

    public CommandInbox(int capacity) {
        this.capacity = capacity;
    }

    /** @return false if the queue is full (the game isn't draining, e.g. paused at the main menu). */
    public boolean offer(Command command) {
        if (size.incrementAndGet() > capacity) {
            size.decrementAndGet();
            return false;
        }
        queue.add(command);
        return true;
    }

    @Override
    public List<Command> drain(int max) {
        List<Command> out = new ArrayList<>(Math.min(max, 16));
        Command next;
        while (out.size() < max && (next = queue.poll()) != null) {
            size.decrementAndGet();
            out.add(next);
        }
        return out;
    }
}
