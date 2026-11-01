package de.tiktak.kmm.tvshow;

import java.util.Objects;

/**
 * Eine Serie aus einer Migration.
 *
 * @param directoryPath absoluter Pfad des Serienordners
 * @param language      Sprache oder {@code null}, dann bleibt eine vorhandene erhalten
 */
public record TvShowImport(long sourceId, String directoryPath, TvShowMetadata metadata, TvShowExtras extras,
        String language) {

    public TvShowImport {
        Objects.requireNonNull(directoryPath, "directoryPath must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        extras = extras == null ? TvShowExtras.EMPTY : extras;
    }

    /** Eine Serie ohne Angabe der Sprache. */
    public TvShowImport(long sourceId, String directoryPath, TvShowMetadata metadata, TvShowExtras extras) {
        this(sourceId, directoryPath, metadata, extras, null);
    }
}
