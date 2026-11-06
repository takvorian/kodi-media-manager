package de.tiktak.kmm.nfo;

import java.util.Objects;

/**
 * The movie set the movie belongs to ({@code <set>}).
 *
 * @param name     name of the set
 * @param overview description or {@code null}
 * @param tmdbId   TMDB ID of the collection ({@code <tmdb>}, as Ember writes it) or {@code null}
 */
public record NfoMovieSet(String name, String overview, String tmdbId) {

    public NfoMovieSet {
        Objects.requireNonNull(name, "name must not be null");
    }

    /** A set without TMDB ID. */
    public NfoMovieSet(String name, String overview) {
        this(name, overview, null);
    }
}
