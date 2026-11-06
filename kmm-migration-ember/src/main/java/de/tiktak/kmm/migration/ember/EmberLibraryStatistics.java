package de.tiktak.kmm.migration.ember;

/**
 * Size of an Ember library as shown before a migration.
 */
public record EmberLibraryStatistics(
        long movies,
        long movieSets,
        long tvShows,
        long episodes,
        long movieSources,
        long tvShowSources) {
}
