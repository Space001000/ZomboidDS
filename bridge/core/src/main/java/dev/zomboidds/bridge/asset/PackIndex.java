package dev.zomboidds.bridge.asset;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads the table of contents of a Project Zomboid {@code .pack} texture atlas.
 *
 * <p>The format below comes from community tooling; verified against 42.20's {@code UI.pack} and
 * {@code UI2.pack}. The sanity limits make a mismatch fail loudly instead of returning garbage.
 * <pre>
 * [ "PZPK" int version ]            optional header; absent means version 0
 * int pageCount
 * per page:
 *   string name                     (int length + bytes)
 *   int entryCount
 *   int hasAlpha
 *   per entry: string name, int x, y, w, h, offsetX, offsetY, fullW, fullH
 *              (sprites are trimmed to their visible pixels; offset/full size describe the original)
 *   version >= 1: int pngLength, png bytes
 *   version 0:    png bytes, int 0xDEADBEEF
 * </pre>
 * Integers are little-endian, except inside the PNG itself.
 */
final class PackIndex {

    record Page(Path file, String name, long pngOffset, int pngLength) {
    }

    /**
     * The sprite occupies {@code width x height} at {@code x,y} on the page, and belongs at
     * {@code offsetX,offsetY} on a {@code fullWidth x fullHeight} canvas.
     */
    record Entry(Page page, String name, int x, int y, int width, int height,
                 int offsetX, int offsetY, int fullWidth, int fullHeight) {
    }

    private static final int MAGIC_PZPK = 0x4B505A50; // "PZPK" read as little-endian int
    private static final int PAGE_END_MARKER = 0xDEADBEEF;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    static Map<String, Entry> read(Path file) throws IOException {
        Map<String, Entry> entries = new HashMap<>();
        try (Reader in = new Reader(Files.newInputStream(file))) {
            int first = in.intLE();
            int version = 0;
            int pageCount = first;
            if (first == MAGIC_PZPK) {
                version = in.intLE();
                pageCount = in.intLE();
            }
            check(pageCount, 0, 100_000, "page count");

            for (int p = 0; p < pageCount; p++) {
                String pageName = in.string();
                int entryCount = check(in.intLE(), 0, 1_000_000, "entry count");
                in.intLE(); // hasAlpha
                Entry[] pageEntries = new Entry[entryCount];
                for (int e = 0; e < entryCount; e++) {
                    String name = in.string();
                    int x = check(in.intLE(), 0, 16384, "x");
                    int y = check(in.intLE(), 0, 16384, "y");
                    int w = check(in.intLE(), 0, 16384, "width");
                    int h = check(in.intLE(), 0, 16384, "height");
                    int ox = check(in.intLE(), 0, 16384, "offsetX");
                    int oy = check(in.intLE(), 0, 16384, "offsetY");
                    int fw = check(in.intLE(), 0, 16384, "full width");
                    int fh = check(in.intLE(), 0, 16384, "full height");
                    pageEntries[e] = new Entry(null, name, x, y, w, h, ox, oy, fw, fh);
                }

                Page page;
                if (version >= 1) {
                    int length = check(in.intLE(), 0, Integer.MAX_VALUE, "png length");
                    page = new Page(file, pageName, in.position(), length);
                    in.skipFully(length);
                } else {
                    long offset = in.position();
                    int length = in.skipPng();
                    page = new Page(file, pageName, offset, length);
                    int marker = in.intLE();
                    if (marker != PAGE_END_MARKER) {
                        throw new IOException("missing page end marker after " + pageName);
                    }
                }
                for (Entry e : pageEntries) {
                    entries.putIfAbsent(e.name(), new Entry(page, e.name(), e.x(), e.y(), e.width(), e.height(),
                            e.offsetX(), e.offsetY(), e.fullWidth(), e.fullHeight()));
                }
            }
        }
        return entries;
    }

    private static int check(int value, int min, int max, String what) throws IOException {
        if (value < min || value > max) {
            throw new IOException("implausible " + what + ": " + value + " (format mismatch?)");
        }
        return value;
    }

    /** Buffered little-endian reader that tracks its position and can skip without reading. */
    private static final class Reader implements AutoCloseable {
        private final InputStream in;
        private long position;

        Reader(InputStream raw) {
            this.in = new BufferedInputStream(raw, 64 * 1024);
        }

        long position() {
            return position;
        }

        int intLE() throws IOException {
            byte[] b = bytes(4);
            return (b[0] & 0xFF) | (b[1] & 0xFF) << 8 | (b[2] & 0xFF) << 16 | (b[3] & 0xFF) << 24;
        }

        int intBE() throws IOException {
            byte[] b = bytes(4);
            return (b[0] & 0xFF) << 24 | (b[1] & 0xFF) << 16 | (b[2] & 0xFF) << 8 | (b[3] & 0xFF);
        }

        String string() throws IOException {
            int length = check(intLE(), 0, 4096, "string length");
            return new String(bytes(length), StandardCharsets.UTF_8);
        }

        byte[] bytes(int n) throws IOException {
            byte[] b = in.readNBytes(n);
            if (b.length != n) {
                throw new EOFException();
            }
            position += n;
            return b;
        }

        void skipFully(long n) throws IOException {
            long remaining = n;
            while (remaining > 0) {
                long skipped = in.skip(remaining);
                if (skipped <= 0) {
                    if (in.read() < 0) {
                        throw new EOFException();
                    }
                    skipped = 1;
                }
                remaining -= skipped;
            }
            position += n;
        }

        /** Skips one PNG by walking its chunks up to IEND. Returns its length in bytes. */
        int skipPng() throws IOException {
            long start = position;
            byte[] signature = bytes(8);
            for (int i = 0; i < 8; i++) {
                if (signature[i] != PNG_SIGNATURE[i]) {
                    throw new IOException("expected PNG data at offset " + start);
                }
            }
            while (true) {
                int chunkLength = check(intBE(), 0, Integer.MAX_VALUE, "PNG chunk length");
                String type = new String(bytes(4), StandardCharsets.US_ASCII);
                skipFully(chunkLength + 4L); // data + CRC
                if ("IEND".equals(type)) {
                    return Math.toIntExact(position - start);
                }
            }
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }

    private PackIndex() {
    }
}
