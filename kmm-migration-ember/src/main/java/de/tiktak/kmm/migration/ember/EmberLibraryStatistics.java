package de.tiktak.kmm.migration.ember;

/**
 * Umfang einer Ember-Bibliothek, wie er vor einer Migration angezeigt wird.
 */
public record EmberLibraryStatistics(
        long movies,
        long movieSets,
        long tvShows,
        long episodes,
        long movieSources,
        long tvShowSources) {
}
