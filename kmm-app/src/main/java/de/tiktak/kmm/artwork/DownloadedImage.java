package de.tiktak.kmm.artwork;

import java.util.Objects;

/**
 * Ein heruntergeladenes Bild.
 *
 * @param extension Dateiendung passend zum Inhalt, z. B. {@code jpg} oder {@code png}
 */
record DownloadedImage(byte[] content, String extension) {

    DownloadedImage {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(extension, "extension must not be null");
    }
}
