package de.tiktak.kmm.tvshow;

import java.util.Objects;

/**
 * What the {@code tvshow.nfo} of a TV show describes: metadata and further details.
 */
public record TvShowContent(TvShowMetadata metadata, TvShowExtras extras) {

    public TvShowContent {
        Objects.requireNonNull(metadata, "metadata must not be null");
        Objects.requireNonNull(extras, "extras must not be null");
    }
}
