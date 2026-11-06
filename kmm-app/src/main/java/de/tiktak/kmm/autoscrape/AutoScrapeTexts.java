package de.tiktak.kmm.autoscrape;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.web.LocalizedTexts;

/**
 * Texts of the auto-scraping results, shared by movies and TV shows.
 */
final class AutoScrapeTexts {

    static final LocalizedText UNEXPECTED_ERROR = LocalizedText.of("autoScrape.unexpectedError");
    static final LocalizedText NO_METADATA_SCRAPER = LocalizedText.of("autoScrape.noMetadataScraper");
    static final LocalizedText NO_TV_SHOW_SCRAPER = LocalizedText.of("autoScrape.noTvShowScraper");

    private AutoScrapeTexts() {
    }

    /** A failure of one entry, shown as "entry: message". */
    static LocalizedText failure(LocalizedText entry, LocalizedText message) {
        return LocalizedText.of("autoScrape.failure").with("entry", entry).with("message", message);
    }

    /** An entry of the selection that no longer exists. */
    static LocalizedText unknownMovie(long movieId) {
        return LocalizedText.of("autoScrape.unknownMovie").with("id", movieId);
    }

    /** An entry of the selection that no longer exists. */
    static LocalizedText unknownShow(long showId) {
        return LocalizedText.of("autoScrape.unknownShow").with("id", showId);
    }

    /**
     * @param failure why the technical details could not be read, as plain string or translatable text
     */
    static LocalizedText technicalDetails(Object failure) {
        return LocalizedText.of("autoScrape.technicalDetails").with("reason", LocalizedTexts.asText(failure));
    }

    static LocalizedText theme(Throwable cause) {
        return LocalizedText.of("autoScrape.theme").with("reason", LocalizedTexts.of(cause));
    }

    static LocalizedText enrichment(Throwable cause) {
        return LocalizedText.of("autoScrape.enrichment").with("reason", LocalizedTexts.of(cause));
    }

    /**
     * @param reason why renaming was skipped or failed, as plain string or translatable text
     */
    static LocalizedText notRenamed(String title, Object reason) {
        return LocalizedText.of("autoScrape.notRenamed").with("title", title)
                .with("reason", LocalizedTexts.asText(reason));
    }

    static LocalizedText nfoNotWritten(String file) {
        return LocalizedText.of("autoScrape.nfoNotWritten").with("file", file);
    }

    static LocalizedText season(int season, String title) {
        return LocalizedText.of("autoScrape.season").with("season", season).with("title", title);
    }
}
