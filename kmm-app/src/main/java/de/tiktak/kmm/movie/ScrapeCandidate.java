package de.tiktak.kmm.movie;

import java.net.URI;

import de.tiktak.kmm.plugin.api.SearchResult;

/**
 * Suchtreffer eines Scrapers für die Auswahl in der Oberfläche.
 */
public record ScrapeCandidate(
        String providerId,
        String title,
        String originalTitle,
        Integer year,
        String overview,
        URI posterUrl) {

    static ScrapeCandidate from(SearchResult result) {
        return new ScrapeCandidate(result.providerId(), result.title(), result.originalTitle(), result.year(),
                result.overview(), result.posterUrl());
    }
}
