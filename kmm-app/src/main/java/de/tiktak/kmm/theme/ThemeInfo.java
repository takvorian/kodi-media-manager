package de.tiktak.kmm.theme;

import java.time.Instant;

/**
 * The {@code theme.mp3} of a movie or a TV show.
 *
 * @param path       path of the file, even if it is missing
 * @param sizeBytes  size or {@code null} without a file
 * @param modifiedAt modification time or {@code null} without a file
 */
public record ThemeInfo(boolean exists, String path, Long sizeBytes, Instant modifiedAt) {
}
