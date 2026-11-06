package de.tiktak.kmm.tag;

import java.util.List;

/**
 * The movies and TV shows with a tag, each sorted by title.
 */
public record TaggedItems(String tag, List<Item> movies, List<Item> shows) {

    public TaggedItems {
        movies = List.copyOf(movies);
        shows = List.copyOf(shows);
    }

    public record Item(long id, String title) {
    }
}
