package de.tiktak.kmm.artwork;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import de.tiktak.kmm.plugin.api.ImageType;

/**
 * The place of an image in the file system: all file names under which Kodi finds it (per extension, for
 * batches also named after the first part). A new image is saved under the first name with its
 * extension.
 *
 * @param candidates possible files, preferred ones first
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

    /** Target file for an image with this extension, e.g. {@code poster.jpg}. */
    Path target(String extension) {
        String suffix = EXTENSION_SEPARATOR + extension.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(suffix))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported image extension: " + extension));
    }
}
