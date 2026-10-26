package de.tiktak.kmm.tvshow;

import java.util.Objects;

/**
 * Eine Serie aus einer Migration.
 *
 * @param directoryPath absoluter Pfad des Serienordners
 */
public record TvShowImport(long sourceId, String directoryPath, TvShowMetadata metadata, TvShowExtras extras) {

    public TvShowImport {
        Objects.requireNonNull(directoryPath, "directoryPath must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        extras = extras == null ? TvShowExtras.EMPTY : extras;
    }
}
