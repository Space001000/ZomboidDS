package dev.zomboidds.bridge.asset;

import dev.zomboidds.bridge.Log;
import dev.zomboidds.bridge.port.IconSource;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cuts icons out of the texture atlases in Project Zomboid {@code .pack} files, straight from disk,
 * so no OpenGL access is needed.
 *
 * <p>Indexing reads only the headers and skips over the page images, and happens on the first
 * request. The most recently decoded page is kept, because icons are usually requested in bursts
 * from the same page.
 */
public final class PackFileIconSource implements IconSource {

    private final List<Path> packFiles;
    private Map<String, PackIndex.Entry> index;
    private PackIndex.Page cachedPage;
    private BufferedImage cachedImage;

    public PackFileIconSource(List<Path> packFiles) {
        this.packFiles = List.copyOf(packFiles);
    }

    @Override
    public synchronized Optional<byte[]> loadPng(String iconName) {
        PackIndex.Entry entry = index().get(iconName);
        if (entry == null) {
            return Optional.empty();
        }
        try {
            BufferedImage page = decode(entry.page());
            BufferedImage sprite = page.getSubimage(entry.x(), entry.y(), entry.width(), entry.height());
            BufferedImage icon = untrimmed(sprite, entry);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(icon, "png", out);
            return Optional.of(out.toByteArray());
        } catch (IOException | RuntimeException e) {
            Log.warn("could not extract " + iconName + " from " + entry.page().file() + ": " + e);
            return Optional.empty();
        }
    }

    /**
     * Puts the trimmed sprite back where it belongs on its original canvas, so every icon has its
     * intended size and alignment (like the game draws them).
     */
    private static BufferedImage untrimmed(BufferedImage sprite, PackIndex.Entry entry) {
        int width = Math.max(entry.fullWidth(), entry.offsetX() + entry.width());
        int height = Math.max(entry.fullHeight(), entry.offsetY() + entry.height());
        if (width == entry.width() && height == entry.height()) {
            return sprite;
        }
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.drawImage(sprite, entry.offsetX(), entry.offsetY(), null);
        } finally {
            g.dispose();
        }
        return canvas;
    }

    @Override
    public synchronized void warmUp() {
        index();
    }

    private BufferedImage decode(PackIndex.Page page) throws IOException {
        if (page != cachedPage) {
            byte[] png = new byte[page.pngLength()];
            try (RandomAccessFile file = new RandomAccessFile(page.file().toFile(), "r")) {
                file.seek(page.pngOffset());
                file.readFully(png);
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null) {
                throw new IOException("page " + page.name() + " is not a readable PNG");
            }
            cachedPage = page;
            cachedImage = image;
        }
        return cachedImage;
    }

    private Map<String, PackIndex.Entry> index() {
        if (index == null) {
            Map<String, PackIndex.Entry> all = new HashMap<>();
            for (Path pack : packFiles) {
                try {
                    PackIndex.read(pack).forEach(all::putIfAbsent);
                } catch (IOException | RuntimeException e) {
                    Log.warn("skipping unreadable pack " + pack.getFileName() + ": " + e.getMessage());
                }
            }
            Log.info("indexed " + all.size() + " textures from " + packFiles.size() + " pack files");
            index = all;
        }
        return index;
    }
}
