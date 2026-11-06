package de.tiktak.kmm.tvshow;

import java.time.Instant;
import java.util.Objects;

/**
 * An episode from a migration.
 *
 * @param filePath absolute path of the episode file
 * @param extras   further details; without tracks the known tracks are kept
 * @param addedAt  time added to the library or {@code null} for "now"
 */
public record EpisodeImport(String filePath, int season, int episode, EpisodeMetadata metadata, EpisodeExtras extras,
        Instant addedAt) {

    public EpisodeImport {
        Objects.requireNonNull(filePath, "filePath must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        extras = extras == null ? EpisodeExtras.EMPTY : extras;
    }
}
