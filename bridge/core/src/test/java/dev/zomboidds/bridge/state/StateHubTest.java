package dev.zomboidds.bridge.state;

import dev.zomboidds.bridge.protocol.JsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateHubTest {

    private final StateHub hub = new StateHub(new JsonCodec(), () -> Map.of("protocol", 1));

    @AfterEach
    void tearDown() {
        hub.shutdown();
    }

    @Test
    void lateClientGetsHelloThenLatestRetainedStateButNoEvents() throws Exception {
        hub.publish("player", Map.of("health", 50));
        hub.publish("player", Map.of("health", 40));
        hub.publish("command_result", Map.of("id", "c-1", "ok", true));
        // An old button press must not make a newly connected app jump to a panel.
        hub.publish("show", Map.of("panel", "inventory"));

        RecordingClient late = connect();
        late.awaitCount(2);

        assertTrue(late.received.get(0).contains("\"type\":\"hello\""));
        assertTrue(late.received.get(1).contains("\"health\":40"));
        Thread.sleep(50);
        assertEquals(2, late.received.size(), "events must not be replayed");
    }

    @Test
    void connectedClientsReceivePublishedMessages() throws Exception {
        RecordingClient client = connect();
        client.awaitCount(1); // hello

        hub.publish("vehicle", Map.of("inVehicle", false));
        client.awaitCount(2);

        assertTrue(client.received.get(1).contains("\"type\":\"vehicle\""));
    }

    private RecordingClient connect() {
        RecordingClient client = new RecordingClient();
        hub.onClientConnected(client);
        return client;
    }

    static final class RecordingClient implements ClientConnection {
        final List<String> received = new CopyOnWriteArrayList<>();

        @Override
        public void send(String json) {
            received.add(json);
        }

        @Override
        public String describe() {
            return "test";
        }

        void awaitCount(int n) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 2000;
            while (received.size() < n && System.currentTimeMillis() < deadline) {
                Thread.sleep(5);
            }
            assertTrue(received.size() >= n, "expected " + n + " messages, got " + received);
        }
    }
}
