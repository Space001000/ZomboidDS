package dev.zomboidds.bridge.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round-trips synthetic packs written in the format documented on {@link PackIndex}. */
class PackFileIconSourceTest {

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;

    @TempDir
    Path dir;

    @Test
    void extractsIconFromVersion1Pack() throws Exception {
        Path pack = writePack(1);
        assertIconIsBlue(new PackFileIconSource(List.of(pack)));
    }

    @Test
    void extractsIconFromHeaderlessVersion0Pack() throws Exception {
        Path pack = writePack(0);
        assertIconIsBlue(new PackFileIconSource(List.of(pack)));
    }

    @Test
    void trimmedSpriteIsPutBackOnItsFullCanvas() throws Exception {
        PackFileIconSource source = new PackFileIconSource(List.of(writePack(1)));
        BufferedImage icon = ImageIO.read(new ByteArrayInputStream(source.loadPng("Item_Trimmed").orElseThrow()));

        assertEquals(4, icon.getWidth());
        assertEquals(4, icon.getHeight());
        assertEquals(0, icon.getRGB(0, 0) >>> 24, "outside the sprite is transparent");
        assertEquals(BLUE, icon.getRGB(1, 1));
        assertEquals(BLUE, icon.getRGB(2, 2));
        assertEquals(0, icon.getRGB(3, 3) >>> 24);
    }

    @Test
    void unknownIconIsEmpty() throws Exception {
        assertTrue(new PackFileIconSource(List.of(writePack(1))).loadPng("Item_Nope").isEmpty());
    }

    private static void assertIconIsBlue(PackFileIconSource source) throws IOException {
        byte[] png = source.loadPng("Item_Blue").orElseThrow();
        BufferedImage icon = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(4, icon.getWidth());
        assertEquals(4, icon.getHeight());
        assertEquals(BLUE, icon.getRGB(0, 0));
        assertEquals(BLUE, icon.getRGB(3, 3));
    }

    /** One 8x4 page: red on the left half ("Item_Red"), blue on the right half ("Item_Blue"). */
    private Path writePack(int version) throws IOException {
        BufferedImage page = new BufferedImage(8, 4, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 4; y++) {
                page.setRGB(x, y, x < 4 ? RED : BLUE);
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(page, "png", png);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (version >= 1) {
            out.write("PZPK".getBytes(StandardCharsets.US_ASCII));
            out.write(le(version));
        }
        out.write(le(1));              // pages
        out.write(str("page0"));
        out.write(le(3));              // entries
        out.write(le(1));              // hasAlpha
        out.write(entry("Item_Red", 0, 0, 4, 4, 0, 0, 4, 4));
        out.write(entry("Item_Blue", 4, 0, 4, 4, 0, 0, 4, 4));
        out.write(entry("Item_Trimmed", 4, 0, 2, 2, 1, 1, 4, 4)); // 2x2 of blue, at 1,1 on a 4x4 canvas
        if (version >= 1) {
            out.write(le(png.size()));
            png.writeTo(out);
        } else {
            png.writeTo(out);
            out.write(le(0xDEADBEEF));
        }

        Path file = dir.resolve("test_v" + version + ".pack");
        Files.write(file, out.toByteArray());
        return file;
    }

    private static byte[] entry(String name, int... xywhOffsetFull) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(str(name));
        for (int v : xywhOffsetFull) {
            out.write(le(v));
        }
        return out.toByteArray();
    }

    private static byte[] str(String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(le(bytes.length));
        out.write(bytes);
        return out.toByteArray();
    }

    private static byte[] le(int v) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array();
    }
}
