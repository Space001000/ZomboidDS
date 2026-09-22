package dev.zomboidds.bridge.protocol;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonCodecTest {

    private final JsonCodec codec = new JsonCodec();

    @Test
    void decodesTheSharedCommandFixture() throws Exception {
        Command command = codec.decodeCommand(fixture("command_equip.json"));

        assertEquals("c-17", command.id());
        assertEquals("equip", command.name());
        assertEquals(10234L, command.args().get("itemId"));
        assertEquals("primary", command.args().get("slot"));
    }

    @Test
    void rejectsOtherProtocolVersionsButKeepsTheIdForTheReply() {
        ProtocolException e = assertThrows(ProtocolException.class, () -> codec.decodeCommand(
                "{\"v\":2,\"type\":\"command\",\"id\":\"c-1\",\"name\":\"equip\"}"));
        assertEquals("c-1", e.commandId());
    }

    @Test
    void rejectsGarbage() {
        assertThrows(ProtocolException.class, () -> codec.decodeCommand("not json {"));
        assertThrows(ProtocolException.class, () -> codec.decodeCommand("[1,2]"));
    }

    @Test
    void encodesTheEnvelope() {
        String json = codec.encode(new OutboundMessage("inventory", 5, Map.of("items", List.of())));
        assertTrue(json.startsWith("{\"v\":1,\"type\":\"inventory\",\"seq\":5,\"data\":"), json);
    }

    static String fixture(String name) throws IOException {
        // Tests run with the module directory as working directory.
        return Files.readString(Path.of("../../protocol/fixtures", name));
    }
}
