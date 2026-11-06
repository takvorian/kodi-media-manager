package de.tiktak.kmm.tvshow;

import java.net.URI;

import de.tiktak.kmm.plugin.api.SearchResult;

/**
 * Search hit of a TV show scraper for the selection in the user interface.
 */
public record TvShowScrapeCandidate(
        String providerId,
        String title,
        String originalTitle,
        Integer year,
        String overview,
        URI posterUrl) {

    static TvShowScrapeCandidate from(SearchResult result) {
        return new TvShowScrapeCandidate(result.providerId(), result.title(), result.originalTitle(), result.year(),
                result.overview(), result.posterUrl());
    }
}
