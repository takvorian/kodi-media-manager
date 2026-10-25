package de.tiktak.kmm.migration.ember.library;

/**
 * Ein Filmset aus Ember.
 *
 * @param plot             Beschreibung oder {@code null}
 * @param tmdbCollectionId TMDB-Collection-ID oder {@code null}
 */
public record EmberMovieSet(long id, String title, String plot, String tmdbCollectionId) {
}
