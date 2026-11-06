package de.tiktak.kmm.autoscrape;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.source.ScanEntry;

/**
 * Result of auto-scraping a TV show source or a selection of TV shows.
 *
 * @param shows            processed TV shows
 * @param scraped          TV shows that were newly identified
 * @param unmatched        TV shows without a unique match
 * @param episodesUpdated  episodes whose empty details were completed
 * @param imagesSaved      newly saved images (TV show, seasons, stills)
 * @param trailersSaved    newly entered trailer links
 * @param themesSaved      newly saved theme music ({@code theme.mp3})
 * @param enriched         TV shows with completed ratings or fields (e.g. Trakt, OMDb)
 * @param unmatchedEntries TV shows without a unique match (truncated)
 * @param failures         errors per TV show as "title: message" (truncated)
 * @param warnings         one-time notes, e.g. about providers that are not set up (truncated)
 */
public record TvAutoScrapeResult(
        int shows,
        int scraped,
        int unmatched,
        int episodesUpdated,
        int imagesSaved,
        int trailersSaved,
        int themesSaved,
        int enriched,
        List<ScanEntry> unmatchedEntries,
        List<LocalizedText> failures,
        List<LocalizedText> warnings) {

    public TvAutoScrapeResult {
        unmatchedEntries = List.copyOf(unmatchedEntries);
        failures = List.copyOf(failures);
        warnings = List.copyOf(warnings);
    }
}
