package de.tiktak.kmm.artwork;

import java.nio.file.Path;

/**
 * An image of a movie set for Kodi.
 *
 * @param kodiName Kodi's name of the image type, e.g. {@code poster}
 * @param file     existing file or {@code null} if the image is missing
 */
public record MovieSetImage(String kodiName, Path file) {
}
