package de.tiktak.kmm.scanner;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A found episode file.
 *
 * @param file   video file
 * @param number season and episode(s) according to the file name
 * @param title  suggested title from the file name or {@code null}
 */
public record EpisodeCandidate(Path file, EpisodeNumber number, String title) {

    public EpisodeCandidate {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(number, "number must not be null");
    }
}
