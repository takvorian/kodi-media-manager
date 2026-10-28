package de.tiktak.kmm.movie;

import java.util.List;

/**
 * Ein Filmset mit seinen Filmen in der Reihenfolge des Sets.
 */
public record MovieSetDetails(long id, MovieSetData data, List<Member> movies) {

    public MovieSetDetails {
        movies = List.copyOf(movies);
    }

    /** Ein Film des Sets. */
    public record Member(long id, String title, Integer year) {
    }
}
