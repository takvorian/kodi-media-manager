package de.tiktak.kmm.artwork;

import java.time.Instant;
import java.util.Optional;

import de.tiktak.kmm.plugin.api.ImageType;

/**
 * Stand einer Bildart eines Films für die Oberfläche.
 *
 * @param path       vorhandene Datei oder {@code null}
 * @param modifiedAt letzte Änderung oder {@code null}; dient der Oberfläche auch als Cache-Schlüssel
 */
public record ArtworkInfo(ImageType type, boolean exists, String path, Instant modifiedAt) {

    static ArtworkInfo of(ImageType type, Optional<ArtworkFile> file) {
        return file.map(found -> new ArtworkInfo(type, true, found.file().toString(), found.modifiedAt()))
                .orElseGet(() -> new ArtworkInfo(type, false, null, null));
    }
}
