package de.tiktak.kmm.migration.ember.library;

/**
 * Ein Filmset aus Ember.
 *
 * @param plot             Beschreibung oder {@code null}
 * @param tmdbCollectionId TMDB-Collection-ID oder {@code null}
 * @param sortedByTitle    ob die Filme nach Titel statt nach Jahr sortiert sind ({@code SortMethod = 1})
 */
public record EmberMovieSet(long id, String title, String plot, String tmdbCollectionId, boolean sortedByTitle) {

    /** Ein nach Jahr sortiertes Set. */
    public EmberMovieSet(long id, String title, String plot, String tmdbCollectionId) {
        this(id, title, plot, tmdbCollectionId, false);
    }
}
