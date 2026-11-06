package de.tiktak.kmm.plugin.api;

import java.util.List;
import java.util.Optional;

/**
 * Supplies details of movie sets (collections such as "The Matrix Collection").
 */
public interface MovieSetScraper extends Scraper {

    /**
     * Searches for matching collections; the user picks one of the results.
     *
     * @throws ScraperException if the provider cannot be reached or rejects the request
     */
    List<SearchResult> searchSets(String title);

    /**
     * Loads title and description of a collection in the configured language.
     *
     * @return empty if the provider does not know the ID
     * @throws ScraperException if the provider cannot be reached or rejects the request
     */
    Optional<MovieSetResult> fetchSet(String providerId);
}
