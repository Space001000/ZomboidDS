package dev.zomboidds.bridge.mock;

import dev.zomboidds.bridge.port.IconSource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

/** Draws a colored tile with the icon's initials, since the mock has no game assets. */
final class PlaceholderIconSource implements IconSource {

    private static final int SIZE = 64;

    @Override
    public Optional<byte[]> loadPng(String iconName) {
        String label = iconName.replaceFirst("^Item_", "");
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.getHSBColor((label.hashCode() & 0xFFFF) / 65535f, 0.45f, 0.55f));
            g.fillRoundRect(2, 2, SIZE - 4, SIZE - 4, 14, 14);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
            String initials = label.substring(0, Math.min(2, label.length()));
            int width = g.getFontMetrics().stringWidth(initials);
            g.drawString(initials, (SIZE - width) / 2, SIZE / 2 + 9);
        } finally {
            g.dispose();
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return Optional.of(out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
