package de.tiktak.kmm.artwork;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.ImageScraper;
import de.tiktak.kmm.plugin.api.ImageType;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fragt alle aktiven Bild-Scraper gleichzeitig ab und führt die Treffer zusammen. Ein ausgefallener
 * Anbieter verhindert nicht die Treffer der anderen, sondern erscheint als Hinweis.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ArtworkCandidateFinder {

    private static final int FIRST = 0;
    private static final int SECOND = 1;
    private static final int ENGLISH = 2;
    private static final int OTHER_LANGUAGE = 3;
    private static final String ENGLISH_LANGUAGE = "en";
    /** Hintergründe sollen in Kodi ohne Schrift sein; dort zählen sprachneutrale Bilder zuerst. */
    private static final Set<ImageType> TEXTLESS_PREFERRED = Set.of(ImageType.FANART);

    private final ScraperRegistry scraperRegistry;
    private final ArtworkProperties properties;

    ArtworkCandidates find(ImageType type, String title, Integer year, Map<String, String> uniqueIds) {
        ScrapeRequest request = new ScrapeRequest(MediaType.MOVIE, title, year, uniqueIds);
        List<ArtworkCandidate> candidates = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<ImageScraper> ready = new ArrayList<>();
        for (ImageScraper scraper : scraperRegistry.imageScrapersFor(MediaType.MOVIE)) {
            scraper.missingConfiguration().ifPresentOrElse(warnings::add, () -> ready.add(scraper));
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<List<ArtworkCandidate>>> results = ready.stream()
                    .map(scraper -> executor.submit(() -> query(scraper, request, type)))
                    .toList();
            for (int index = 0; index < results.size(); index++) {
                collect(ready.get(index), results.get(index), candidates, warnings);
            }
        }
        candidates.sort(order(type));
        return new ArtworkCandidates(candidates, warnings);
    }

    private static List<ArtworkCandidate> query(ImageScraper scraper, ScrapeRequest request, ImageType type) {
        return scraper.findImages(request).stream()
                .filter(image -> image.type() == type)
                .map(image -> ArtworkCandidate.from(scraper, image))
                .toList();
    }

    private static void collect(ImageScraper scraper, Future<List<ArtworkCandidate>> result,
            List<ArtworkCandidate> candidates, List<String> warnings) {
        try {
            candidates.addAll(result.get());
        } catch (ExecutionException e) {
            String message = e.getCause() instanceof ScraperException scraperProblem
                    ? scraperProblem.getMessage()
                    : "unerwarteter Fehler";
            log.warn("Image scraper {} failed", scraper.id(), e.getCause());
            warnings.add("%s: %s".formatted(scraper.displayName(), message));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            warnings.add("%s: Abfrage unterbrochen".formatted(scraper.displayName()));
        }
    }

    /**
     * Bevorzugte Sprache und sprachneutrale Bilder zuerst (bei Fanart umgekehrt), dann englische und
     * übrige; innerhalb jeder Gruppe die beliebtesten.
     */
    private Comparator<ArtworkCandidate> order(ImageType type) {
        boolean textlessFirst = TEXTLESS_PREFERRED.contains(type);
        return Comparator.comparingInt((ArtworkCandidate candidate) -> languageRank(candidate, textlessFirst))
                .thenComparing(candidate -> Objects.requireNonNullElse(candidate.rating(), 0.0),
                        Comparator.reverseOrder());
    }

    private int languageRank(ArtworkCandidate candidate, boolean textlessFirst) {
        if (candidate.language() == null) {
            return textlessFirst ? FIRST : SECOND;
        }
        if (candidate.language().equalsIgnoreCase(properties.preferredLanguage())) {
            return textlessFirst ? SECOND : FIRST;
        }
        return candidate.language().equalsIgnoreCase(ENGLISH_LANGUAGE) ? ENGLISH : OTHER_LANGUAGE;
    }
}
