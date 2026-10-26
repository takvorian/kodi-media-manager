package de.tiktak.kmm.artwork;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import de.tiktak.kmm.plugin.api.ImageType;

/**
 * Der Platz eines Bildes im Dateisystem: alle Dateinamen, unter denen Kodi es findet (je Endung, bei
 * Stapeln auch nach dem ersten Teil benannt). Ein neues Bild wird unter dem ersten Namen mit seiner
 * Endung gespeichert.
 *
 * @param candidates mögliche Dateien, die bevorzugten zuerst
 */
record ArtworkSlot(ImageType type, List<Path> candidates) {

    private static final char EXTENSION_SEPARATOR = '.';

    ArtworkSlot {
        Objects.requireNonNull(type, "type must not be null");
        candidates = List.copyOf(candidates);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates must not be empty");
        }
    }

    /** Zieldatei für ein Bild mit dieser Endung, z. B. {@code poster.jpg}. */
    Path target(String extension) {
        String suffix = EXTENSION_SEPARATOR + extension.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(suffix))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported image extension: " + extension));
    }
}
