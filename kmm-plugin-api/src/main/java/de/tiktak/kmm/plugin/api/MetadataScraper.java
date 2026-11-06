package de.tiktak.kmm.plugin.api;

import java.util.List;
import java.util.Optional;

/**
 * Supplies metadata (title, plot, IDs …) for a medium.
 */
public interface MetadataScraper extends Scraper {

    /**
     * Searches for matching media; the user picks one of the results.
     *
     * @throws ScraperException if the provider cannot be reached or rejects the request
     */
    List<SearchResult> search(SearchQuery query);

    /**
     * Loads all metadata of a result from {@link #search(SearchQuery)}.
     *
     * @return empty if the provider does not know the ID
     * @throws ScraperException if the provider cannot be reached or rejects the request
     */
    Optional<MetadataResult> fetch(String providerId);

    /**
     * Identifies the medium on its own, preferably via known IDs, otherwise via the best search result. Meant for
     * applying data automatically without asking.
     *
     * @return empty if no result is unambiguous enough
     */
    Optional<MetadataResult> scrape(ScrapeRequest request);
}
