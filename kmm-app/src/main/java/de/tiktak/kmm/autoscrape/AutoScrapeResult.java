package de.tiktak.kmm.autoscrape;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.source.ScanEntry;

/**
 * Result of auto-scraping a movie source or a selection of movies.
 *
 * @param movies           processed movies
 * @param scraped          movies whose metadata was fetched anew
 * @param unmatched        movies without a unique match
 * @param imagesSaved      newly saved images
 * @param trailersSaved    newly entered trailer links
 * @param themesSaved      newly saved theme music ({@code theme.mp3})
 * @param enriched         movies with completed ratings or fields (e.g. Trakt, OMDb)
 * @param unmatchedEntries movies without a unique match (truncated)
 * @param failures         errors per movie as "title: message" (truncated)
 * @param warnings         one-time notes, e.g. about providers that are not set up (truncated)
 */
public record AutoScrapeResult(
        int movies,
        int scraped,
        int unmatched,
        int imagesSaved,
        int trailersSaved,
        int themesSaved,
        int enriched,
        List<ScanEntry> unmatchedEntries,
        List<LocalizedText> failures,
        List<LocalizedText> warnings) {

    public AutoScrapeResult {
        unmatchedEntries = List.copyOf(unmatchedEntries);
        failures = List.copyOf(failures);
        warnings = List.copyOf(warnings);
    }
}
