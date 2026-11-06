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

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.ThemeScraper;
import de.tiktak.kmm.plugin.api.Titles;
import lombok.RequiredArgsConstructor;

/**
 * Searches theme music at all theme music scrapers, with the title and, if different, the original title.
 */
@Service
@RequiredArgsConstructor
class ThemeFinder {

    private final ScraperRegistry scraperRegistry;

    /** Suggestions, matches with the same title first, otherwise in the order of the providers; each source once. */
    ThemeOptions find(ThemeTarget target) {
        List<LocalizedText> warnings = new ArrayList<>();
        Map<String, ThemeOption> options = new LinkedHashMap<>();
        for (ThemeScraper scraper : scraperRegistry.themeScrapersFor(target.mediaType())) {
            var missing = scraper.missingConfiguration();
            if (missing.isPresent()) {
                warnings.add(hint(missing.get()));
                continue;
            }
            for (String title : searchTitles(target)) {
                try {
                    scraper.findThemes(new ScrapeRequest(target.mediaType(), title, target.year(), Map.of()))
                            .forEach(candidate -> options.putIfAbsent(candidate.source().toString(),
                                    ThemeOption.of(candidate, scraper.id(), scraper.displayName(),
                                            sameTitle(candidate.subject(), target))));
                } catch (ScraperException e) {
                    warnings.add(LocalizedText.of("theme.warning")
                            .with("provider", scraper.displayName())
                            .with("reason", e.localizedText()));
                }
            }
        }
        List<ThemeOption> sorted = options.values().stream()
                .sorted(Comparator.comparing(option -> !option.sameTitle()))
                .toList();
        return new ThemeOptions(sorted, warnings);
    }

    /** Plugins word the hint as a translatable text or as plain text. */
    private static LocalizedText hint(Object hint) {
        return hint instanceof LocalizedText text ? text : LocalizedText.literal(String.valueOf(hint));
    }

    /** The first match with the same title; only this one KMM takes over without asking. */
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
