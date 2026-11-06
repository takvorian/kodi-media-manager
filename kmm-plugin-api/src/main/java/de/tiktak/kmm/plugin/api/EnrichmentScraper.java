package de.tiktak.kmm.plugin.api;

import java.util.Optional;
import java.util.Set;

/**
 * Adds ratings to movies and TV shows via known IDs ({@link ScrapeRequest#uniqueIds()}) and, if the provider can,
 * details for empty fields, e.g. Trakt or OMDb. One call returns everything because some providers allow only a few
 * requests per day.
 */
public interface EnrichmentScraper extends Scraper {

    int DEFAULT_ORDER = 100;

    /** Identifiers of the ratings in the NFO, e.g. {@code trakt} or {@code imdb}. */
    Set<String> ratingSources();

    /** Whether the provider also supplies details for empty fields. */
    default boolean providesDetails() {
        return false;
    }

    /** Order of the providers for empty fields: lower numbers first, so German details come before English ones. */
    default int order() {
        return DEFAULT_ORDER;
    }

    /**
     * @return the additions, or empty if the provider does not know the entry
     * @throws ScraperException if the provider cannot be reached or rejects the request
     */
    Optional<Enrichment> findEnrichment(ScrapeRequest request);
}
