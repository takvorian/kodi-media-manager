package de.tiktak.kmm.migration.ember.library;

/**
 * A movie set from Ember.
 *
 * @param plot             plot or {@code null}
 * @param tmdbCollectionId TMDB collection ID or {@code null}
 * @param sortedByTitle    whether the movies are sorted by title instead of year ({@code SortMethod = 1})
 */
public record EmberMovieSet(long id, String title, String plot, String tmdbCollectionId, boolean sortedByTitle) {

    /** A set sorted by year. */
    public EmberMovieSet(long id, String title, String plot, String tmdbCollectionId) {
        this(id, title, plot, tmdbCollectionId, false);
    }
}
