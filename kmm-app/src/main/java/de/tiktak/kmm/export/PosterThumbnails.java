package de.tiktak.kmm.export;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Verkleinert Poster für die HTML-Liste, damit das ZIP klein bleibt.
 */
@Slf4j
@Component
class PosterThumbnails {

    static final int WIDTH = 160;

    /** @return das verkleinerte Poster als JPEG oder leer, wenn die Datei nicht lesbar oder kein Bild ist */
    Optional<byte[]> thumbnail(Path poster) {
        try {
            BufferedImage image = ImageIO.read(poster.toFile());
            if (image == null) {
                return Optional.empty();
            }
            int height = Math.max(1, Math.round((float) image.getHeight() * WIDTH / image.getWidth()));
            BufferedImage scaled = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = scaled.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(image, 0, 0, WIDTH, height, null);
            graphics.dispose();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(scaled, "jpg", bytes);
            return Optional.of(bytes.toByteArray());
        } catch (IOException | RuntimeException e) {
            log.warn("Poster {} not exported: {}", poster, e.getMessage());
            return Optional.empty();
        }
    }
}
