package de.tiktak.kmm.tvshow;

import java.util.List;

/**
 * Ergebnis des automatischen Scrapens einer Serie.
 *
 * @param episodesUpdated Episoden, bei denen leere Angaben ergänzt wurden
 * @param failedFiles     NFOs, die nicht geschrieben werden konnten
 */
public record TvShowAutoScrapeOutcome(Status status, int episodesUpdated, List<String> failedFiles) {

    public TvShowAutoScrapeOutcome {
        failedFiles = List.copyOf(failedFiles);
    }

    static TvShowAutoScrapeOutcome of(Status status) {
        return new TvShowAutoScrapeOutcome(status, 0, List.of());
    }

    /**
     * Wie die Serie zugeordnet wurde.
     */
    public enum Status {
        /** Die Serie wurde über einen eindeutigen Treffer neu zugeordnet. */
        SCRAPED,
        /** Die Serie war schon zugeordnet; nur leere Episodenfelder wurden ergänzt. */
        ALREADY_IDENTIFIED,
        /** Kein eindeutiger Treffer. */
        NO_MATCH,
        /** Kein Serien-Scraper ist eingerichtet. */
        NO_SCRAPER
    }
}
