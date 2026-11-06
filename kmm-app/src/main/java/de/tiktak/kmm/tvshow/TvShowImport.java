package de.tiktak.kmm.tvshow;

import java.util.Objects;

/**
 * A TV show from a migration.
 *
 * @param directoryPath absolute path of the TV show folder
 * @param language      language, or {@code null} to keep an existing one
 */
public record TvShowImport(long sourceId, String directoryPath, TvShowMetadata metadata, TvShowExtras extras,
        String language) {

    public TvShowImport {
        Objects.requireNonNull(directoryPath, "directoryPath must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        extras = extras == null ? TvShowExtras.EMPTY : extras;
    }

    /** A TV show without a language. */
    public TvShowImport(long sourceId, String directoryPath, TvShowMetadata metadata, TvShowExtras extras) {
        this(sourceId, directoryPath, metadata, extras, null);
    }
}
