package de.tiktak.kmm.artwork;

import java.util.Objects;

/**
 * A downloaded image.
 *
 * @param extension file extension matching the content, e.g. {@code jpg} or {@code png}
 */
record DownloadedImage(byte[] content, String extension) {

    DownloadedImage {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(extension, "extension must not be null");
    }
}
