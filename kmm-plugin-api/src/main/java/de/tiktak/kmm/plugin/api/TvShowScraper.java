package de.tiktak.kmm.plugin.api;

import java.util.List;
import java.util.Optional;

/**
 * Supplies metadata of TV shows including seasons and episodes. Language and episode order come from the settings
 * ({@link PluginContext#setting(String)}: {@link #TV_LANGUAGE_SETTING}, {@link #EPISODE_ORDERING_SETTING}).
 */
public interface TvShowScraper extends Scraper {

    /** Language of the TV show metadata, e.g. {@code de-DE}. */
    String TV_LANGUAGE_SETTING = "tv-language";

    /** Episode order: {@code AIRED}, {@code DVD} or {@code ABSOLUTE}. */
    String EPISODE_ORDERING_SETTING = "episode-ordering";

    /**
     * Searches for matching TV shows; the user picks one of the results.
     *
     * @throws ScraperException if the provider cannot be reached or rejects the request
     */
    List<SearchResult> search(SearchQuery query);

    /**
     * Loads the TV show of a result from {@link #search(SearchQuery)} with all episodes.
     *
     * @return empty if the provider does not know the ID
     * @throws ScraperException if the provider cannot be reached or rejects the request
     */
    Optional<TvShowResult> fetch(String providerId);

    /**
     * Identifies the TV show on its own, preferably via known IDs, otherwise via the unambiguously matching search
     * result.
     *
     * @return empty if no result is unambiguous enough
     */
    Optional<TvShowResult> scrape(ScrapeRequest request);

    /**
     * Adds details the provider only supplies per episode (at TVDB directors, writers and guest stars). KMM only
     * requests episodes that lack these details because every request burdens the provider.
     *
     * @param episode an episode from {@link #fetch(String)} or {@link #scrape(ScrapeRequest)}
     * @return the completed episode; the given one if there are no further details
     * @throws ScraperException if the provider cannot be reached or rejects the request
     */
    default EpisodeResult episodeDetails(EpisodeResult episode) {
        return episode;
    }
}
