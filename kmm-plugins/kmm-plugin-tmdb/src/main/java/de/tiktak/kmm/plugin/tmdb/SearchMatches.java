package de.tiktak.kmm.plugin.tmdb;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.SearchResult;

/**
 * Wählt beim automatischen Scrapen den Suchtreffer aus.
 */
final class SearchMatches {

    private SearchMatches() {
    }

    /**
     * Nur ein Treffer mit gleichem Titel (bzw. Originaltitel) und passendem Jahr gilt als sicher;
     * lieber kein Ergebnis als ein falsches.
     */
    static Optional<SearchResult> bestMatch(List<SearchResult> results, ScrapeRequest request) {
        String title = normalize(request.title());
        return results.stream()
                .filter(result -> title.equals(normalize(result.title()))
                        || (result.originalTitle() != null && title.equals(normalize(result.originalTitle()))))
                .filter(result -> request.year() == null || request.year().equals(result.year()))
                .findFirst();
    }

    private static String normalize(String title) {
        return title.strip().toLowerCase(Locale.ROOT);
    }
}
