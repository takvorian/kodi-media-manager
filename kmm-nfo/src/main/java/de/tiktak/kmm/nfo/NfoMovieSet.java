package de.tiktak.kmm.nfo;

import java.util.Objects;

/**
 * Das Filmset, zu dem der Film gehört ({@code <set>}).
 *
 * @param name     Name des Sets
 * @param overview Beschreibung oder {@code null}
 */
public record NfoMovieSet(String name, String overview) {

    public NfoMovieSet {
        Objects.requireNonNull(name, "name must not be null");
    }
}
