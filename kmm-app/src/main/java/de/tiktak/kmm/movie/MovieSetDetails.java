package de.tiktak.kmm.movie;

import java.util.List;

/**
 * A movie set with its movies in the order of the set.
 */
public record MovieSetDetails(long id, MovieSetData data, List<Member> movies) {

    public MovieSetDetails {
        movies = List.copyOf(movies);
    }

    /** A movie of the set. */
    public record Member(long id, String title, Integer year) {
    }
}
