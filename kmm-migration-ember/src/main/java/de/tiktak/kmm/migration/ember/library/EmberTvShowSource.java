package de.tiktak.kmm.migration.ember.library;

/**
 * A TV show source from Ember.
 *
 * @param path     directory as Ember stored it (Windows)
 * @param language language of the source, e.g. {@code de-DE}
 */
public record EmberTvShowSource(long id, String name, String path, String language) {
}
