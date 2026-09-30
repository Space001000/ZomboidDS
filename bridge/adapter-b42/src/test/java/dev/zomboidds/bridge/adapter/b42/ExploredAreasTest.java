package dev.zomboidds.bridge.adapter.b42;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExploredAreasTest {

    @Test
    void encodesAsBase64OfZlibThatTheAppInflates() throws Exception {
        // A whole Muldraugh-sized map (78 x 63 cells, 8 units each, 4 units per byte), a little explored.
        byte[] bits = new byte[78 * 8 / 4 * 63 * 8];
        bits[1234] = 0b0101;
        bits[40_000] = (byte) 0b1000_0000;

        String encoded = ExploredAreas.encode(bits);

        Inflater inflater = new Inflater();
        inflater.setInput(Base64.getDecoder().decode(encoded));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!inflater.finished()) {
            out.write(buffer, 0, inflater.inflate(buffer));
        }
        inflater.end();
        assertArrayEquals(bits, out.toByteArray());
        assertTrue(encoded.length() < 2_000, "mostly unexplored compresses to little: " + encoded.length());
    }
}
