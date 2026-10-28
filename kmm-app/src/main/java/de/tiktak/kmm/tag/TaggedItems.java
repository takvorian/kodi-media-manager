package de.tiktak.kmm.tag;

import java.util.List;

/**
 * Die Filme und Serien mit einem Tag, jeweils nach Titel sortiert.
 */
public record TaggedItems(String tag, List<Item> movies, List<Item> shows) {

    public TaggedItems {
        movies = List.copyOf(movies);
        shows = List.copyOf(shows);
    }

    public record Item(long id, String title) {
    }
}
