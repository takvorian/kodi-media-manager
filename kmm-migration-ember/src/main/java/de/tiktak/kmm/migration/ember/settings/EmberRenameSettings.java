package de.tiktak.kmm.migration.ember.settings;

/**
 * Settings of Ember's renamer (add-on {@code generic.EmberCore.BulkRename}) in Ember's pattern syntax, e.g.
 * {@code $T {($Y)}}. Values not set are {@code null}.
 *
 * @param movieFolder    folder of a movie
 * @param movieFile      files of a movie
 * @param tvShowFolder   folder of a TV show
 * @param seasonFolder   folder of a season
 * @param episodeFile    files of an episode
 * @param movieOnEdit    rename movies after editing
 * @param movieOnScrape  rename movies after scraping (single or multiple)
 * @param tvOnEdit       rename episodes after editing
 * @param tvOnScrape     rename TV shows and episodes after scraping
 * @param episodesOnScan rename new episodes when scanning
 */
public record EmberRenameSettings(
        String movieFolder,
        String movieFile,
        String tvShowFolder,
        String seasonFolder,
        String episodeFile,
        Boolean movieOnEdit,
        Boolean movieOnScrape,
        Boolean tvOnEdit,
        Boolean tvOnScrape,
        Boolean episodesOnScan) {
}
