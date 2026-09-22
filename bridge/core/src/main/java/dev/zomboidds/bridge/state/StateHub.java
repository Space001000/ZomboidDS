package dev.zomboidds.bridge.state;

import dev.zomboidds.bridge.Log;
import dev.zomboidds.bridge.port.StatePublisher;
import dev.zomboidds.bridge.protocol.JsonCodec;
import dev.zomboidds.bridge.protocol.OutboundMessage;
import dev.zomboidds.bridge.protocol.Protocol;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Takes messages from the game thread and delivers them to clients on a single sender thread.
 *
 * <ul>
 *   <li>{@link #publish} only enqueues, so the game thread never waits on the network.</li>
 *   <li>State messages are coalesced: if the sender falls behind, only the newest of each type is sent.</li>
 *   <li>The newest state of each type is retained and replayed to clients that connect later.</li>
 *   <li>All socket writes happen on the sender thread, so per-client ordering is guaranteed.</li>
 * </ul>
 */
public final class StateHub implements StatePublisher {

    private final JsonCodec codec;
    private final Supplier<Object> helloData;

    /** Clients that have received hello + replay. Only these get broadcasts. */
    private final Set<ClientConnection> ready = new CopyOnWriteArraySet<>();

    private final AtomicLong seq = new AtomicLong();
    private final Map<String, OutboundMessage> retained = new ConcurrentHashMap<>();
    private final Map<String, OutboundMessage> pendingState = new ConcurrentHashMap<>();
    private final Queue<OutboundMessage> pendingEvents = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean flushScheduled = new AtomicBoolean();
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ZomboidDS-sender");
        t.setDaemon(true);
        return t;
    });

    public StateHub(JsonCodec codec, Supplier<Object> helloData) {
        this.codec = codec;
        this.helloData = helloData;
    }

    @Override
    public void publish(String type, Object data) {
        OutboundMessage message = new OutboundMessage(type, seq.incrementAndGet(), data);
        if (Protocol.isRetained(type)) {
            retained.put(type, message);
            pendingState.put(type, message);
        } else {
            pendingEvents.add(message);
        }
        scheduleFlush();
    }

    @Override
    public void clearRetained() {
        retained.clear();
        pendingState.clear();
    }

    /**
     * Sends {@code hello} plus all retained state to a newly connected client, then includes it
     * in broadcasts. Runs on the sender thread, so no broadcast can overtake the hello.
     */
    public void onClientConnected(ClientConnection client) {
        sender.execute(() -> {
            client.send(codec.encode(new OutboundMessage(Protocol.TYPE_HELLO, seq.incrementAndGet(), helloData.get())));
            retained.values().stream()
                    .sorted(Comparator.comparingLong(OutboundMessage::seq))
                    .forEach(m -> client.send(codec.encode(m)));
            ready.add(client);
        });
    }

    /** Queued behind any pending connect for the same client, so it can't be re-added afterwards. */
    public void onClientDisconnected(ClientConnection client) {
        sender.execute(() -> ready.remove(client));
    }

    /** Sends a message to one client only (e.g. an error for a command it sent). */
    public void sendTo(ClientConnection client, String type, Object data) {
        OutboundMessage message = new OutboundMessage(type, seq.incrementAndGet(), data);
        sender.execute(() -> client.send(codec.encode(message)));
    }

    public void shutdown() {
        sender.shutdownNow();
    }

    private void scheduleFlush() {
        if (flushScheduled.compareAndSet(false, true)) {
            sender.execute(this::flush);
        }
    }

    private void flush() {
        // Reset first: anything published while we flush schedules another round.
        flushScheduled.set(false);

        List<OutboundMessage> batch = new ArrayList<>();
        OutboundMessage event;
        while ((event = pendingEvents.poll()) != null) {
            batch.add(event);
        }
        for (String type : pendingState.keySet()) {
            OutboundMessage state = pendingState.remove(type);
            if (state != null) {
                batch.add(state);
            }
        }
        batch.sort(Comparator.comparingLong(OutboundMessage::seq));

        if (ready.isEmpty()) {
            return;
        }
        for (OutboundMessage message : batch) {
            String json;
            try {
                json = codec.encode(message);
            } catch (RuntimeException e) {
                Log.error("could not encode '" + message.type() + "'", e);
                continue;
            }
            for (ClientConnection client : ready) {
                client.send(json);
            }
        }
    }
}
