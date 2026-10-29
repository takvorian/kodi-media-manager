package de.tiktak.kmm.theme;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.ThemeScraper;
import de.tiktak.kmm.plugin.api.Titles;
import lombok.RequiredArgsConstructor;

/**
 * Sucht Titelmusik bei allen Titelmusik-Scrapern, mit dem Titel und, falls anders, dem Originaltitel.
 */
@Service
@RequiredArgsConstructor
class ThemeFinder {

    private final ScraperRegistry scraperRegistry;

    /** Vorschläge, Treffer mit gleichem Titel zuerst, sonst in der Reihenfolge der Anbieter; jede Quelle einmal. */
    ThemeOptions find(ThemeTarget target) {
        List<String> warnings = new ArrayList<>();
        Map<String, ThemeOption> options = new LinkedHashMap<>();
        for (ThemeScraper scraper : scraperRegistry.themeScrapersFor(target.mediaType())) {
            Optional<String> missing = scraper.missingConfiguration();
            if (missing.isPresent()) {
                warnings.add(missing.get());
                continue;
            }
            for (String title : searchTitles(target)) {
                try {
                    scraper.findThemes(new ScrapeRequest(target.mediaType(), title, target.year(), Map.of()))
                            .forEach(candidate -> options.putIfAbsent(candidate.source().toString(),
                                    ThemeOption.of(candidate, scraper.id(), scraper.displayName(),
                                            sameTitle(candidate.subject(), target))));
                } catch (ScraperException e) {
                    warnings.add("%s: %s".formatted(scraper.displayName(), e.getMessage()));
                }
            }
        }
        List<ThemeOption> sorted = options.values().stream()
                .sorted(Comparator.comparing(option -> !option.sameTitle()))
                .toList();
        return new ThemeOptions(sorted, warnings);
    }

    /** Der erste Treffer mit gleichem Titel; nur ihn übernimmt KMM ohne Rückfrage. */
    static Optional<ThemeOption> safeMatch(ThemeOptions options) {
        return options.candidates().stream().filter(ThemeOption::sameTitle).findFirst();
    }

    private static List<String> searchTitles(ThemeTarget target) {
        return Stream.of(target.title(), target.originalTitle())
                .filter(title -> title != null && !title.isBlank())
                .filter(distinctNormalized())
                .toList();
    }

    private static Predicate<String> distinctNormalized() {
        Set<String> seen = new HashSet<>();
        return title -> seen.add(Titles.normalize(title));
    }

    private static boolean sameTitle(String subject, ThemeTarget target) {
        return Titles.same(subject, target.title()) || Titles.same(subject, target.originalTitle());
    }
}
