package de.tiktak.kmm.migration;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Result of an Ember import or dry run.
 *
 * @param dryRun           whether nothing was saved
 * @param sourcesCreated   newly created sources
 * @param sourcesReused    existing sources with the same directory
 * @param moviesCreated    newly created movies
 * @param moviesUpdated    existing movies whose details were taken over from Ember
 * @param moviesSkipped    movies without path mapping or without an imported source
 * @param moviesWithoutNfo movies whose file or NFO KMM did not find
 * @param tvShows          result for TV shows and episodes
 * @param kodiHostsCreated newly created Kodi devices
 * @param kodiHostsUpdated existing Kodi devices with the same name that were updated
 * @param settingsChanges  imported settings in readable form
 * @param skippedSources   sources that were not imported, with the reason
 * @param missingFiles     names of the movies without a found NFO (shortened)
 * @param warnings         further notes (shortened)
 */
public record EmberImportReport(
        boolean dryRun,
        int sourcesCreated,
        int sourcesReused,
        int moviesCreated,
        int moviesUpdated,
        int moviesSkipped,
        int moviesWithoutNfo,
        TvShows tvShows,
        int kodiHostsCreated,
        int kodiHostsUpdated,
        List<LocalizedText> settingsChanges,
        List<LocalizedText> skippedSources,
        List<String> missingFiles,
        List<LocalizedText> warnings) {

    public EmberImportReport {
        settingsChanges = List.copyOf(settingsChanges);
        skippedSources = List.copyOf(skippedSources);
        missingFiles = List.copyOf(missingFiles);
        warnings = List.copyOf(warnings);
    }

    /**
     * @param showsSkipped    TV shows without path mapping or without an imported source
     * @param episodesUpdated existing episodes whose details were taken over from Ember
     * @param episodesSkipped episodes without path mapping or of a TV show that was not imported
     * @param episodesMissing episodes that Ember knows without a file; they are not imported
     */
    public record TvShows(int showsCreated, int showsUpdated, int showsSkipped, int episodesCreated,
            int episodesUpdated, int episodesSkipped, int episodesMissing) {
    }
}
