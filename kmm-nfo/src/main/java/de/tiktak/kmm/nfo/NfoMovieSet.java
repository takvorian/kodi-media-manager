package de.tiktak.kmm.nfo;

import java.util.Objects;

/**
 * Das Filmset, zu dem der Film gehört ({@code <set>}).
 *
 * @param name     Name des Sets
 * @param overview Beschreibung oder {@code null}
 * @param tmdbId   TMDB-ID der Sammlung ({@code <tmdb>}, wie Ember sie schreibt) oder {@code null}
 */
public record NfoMovieSet(String name, String overview, String tmdbId) {

    public NfoMovieSet {
        Objects.requireNonNull(name, "name must not be null");
    }

    /** Ein Set ohne TMDB-ID. */
    public NfoMovieSet(String name, String overview) {
        this(name, overview, null);
    }
}
