package de.tiktak.kmm.artwork;

import java.time.Instant;
import java.util.Optional;

import de.tiktak.kmm.plugin.api.ImageType;

/**
 * State of an image type of a movie for the UI.
 *
 * @param path       existing file or {@code null}
 * @param modifiedAt last change or {@code null}; the UI also uses it as a cache key
 */
public record ArtworkInfo(ImageType type, boolean exists, String path, Instant modifiedAt) {

    static ArtworkInfo of(ImageType type, Optional<ArtworkFile> file) {
        return file.map(found -> new ArtworkInfo(type, true, found.file().toString(), found.modifiedAt()))
                .orElseGet(() -> new ArtworkInfo(type, false, null, null));
    }
}
