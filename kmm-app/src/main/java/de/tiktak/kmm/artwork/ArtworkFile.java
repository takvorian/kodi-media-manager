package de.tiktak.kmm.artwork;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

import org.springframework.http.MediaType;

import de.tiktak.kmm.plugin.api.ImageType;

/**
 * Eine vorhandene Bilddatei eines Films.
 */
record ArtworkFile(ImageType type, Path file, Instant modifiedAt) {

    private static final String PNG_EXTENSION = ".png";

    ArtworkFile {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(modifiedAt, "modifiedAt must not be null");
    }

    MediaType mediaType() {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(PNG_EXTENSION)
                ? MediaType.IMAGE_PNG
                : MediaType.IMAGE_JPEG;
    }
}
