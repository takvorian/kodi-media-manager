package de.tiktak.kmm.tvshow;

import java.util.List;

/**
 * Result of automatically scraping a TV show.
 *
 * @param episodesUpdated episodes whose empty fields were filled in
 * @param failedFiles     NFOs that could not be written
 */
public record TvShowAutoScrapeOutcome(Status status, int episodesUpdated, List<String> failedFiles) {

    public TvShowAutoScrapeOutcome {
        failedFiles = List.copyOf(failedFiles);
    }

    static TvShowAutoScrapeOutcome of(Status status) {
        return new TvShowAutoScrapeOutcome(status, 0, List.of());
    }

    /**
     * How the TV show was identified.
     */
    public enum Status {
        /** The TV show was newly identified via a unique match. */
        SCRAPED,
        /** The TV show was already identified; only empty episode fields were filled in. */
        ALREADY_IDENTIFIED,
        /** No unique match. */
        NO_MATCH,
        /** No TV show scraper is set up. */
        NO_SCRAPER
    }
}
