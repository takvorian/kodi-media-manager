package de.tiktak.kmm.migration.ember.library;

/**
 * Eine Filmquelle aus Ember.
 *
 * @param path     Verzeichnis, wie Ember es gespeichert hat (Windows)
 * @param language Sprache der Quelle, z. B. {@code de-DE}
 */
public record EmberMovieSource(long id, String name, String path, String language) {
}
