package de.tiktak.kmm.tvshow;

import java.util.Objects;

/**
 * Was die {@code tvshow.nfo} einer Serie beschreibt: Metadaten und weitere Angaben.
 */
public record TvShowContent(TvShowMetadata metadata, TvShowExtras extras) {

    public TvShowContent {
        Objects.requireNonNull(metadata, "metadata must not be null");
        Objects.requireNonNull(extras, "extras must not be null");
    }
}
