package de.tiktak.kmm.enrichment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.Enrichment;
import de.tiktak.kmm.plugin.api.EnrichmentScraper;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ProviderRating;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Holt Ergänzungen (Bewertungen, Angaben für leere Felder) bei den Ergänzungs-Scrapern, etwa Trakt und OMDb. Nicht
 * eingerichtete oder nicht erreichbare Anbieter werden übergangen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnrichmentFinder {

    private final ScraperRegistry scraperRegistry;

    /**
     * @param uniqueIds bekannte IDs je Anbieter; ohne IDs wird nicht gesucht
     * @param wanted    welche Anbieter gefragt werden, etwa nur die mit fehlenden Bewertungen
     * @return die Ergänzungen in der Reihenfolge der Anbieter
     */
    public List<Enrichment> find(MediaType mediaType, String title, Integer year, Map<String, String> uniqueIds,
            Predicate<EnrichmentScraper> wanted) {
        if (uniqueIds.isEmpty()) {
            return List.of();
        }
        List<Enrichment> found = new ArrayList<>();
        List<EnrichmentScraper> scrapers = scraperRegistry.enrichmentScrapersFor(mediaType).stream()
                .sorted(Comparator.comparingInt(EnrichmentScraper::order)).toList();
        for (EnrichmentScraper scraper : scrapers) {
            if (scraper.missingConfiguration().isPresent() || !wanted.test(scraper)) {
                continue;
            }
            try {
                scraper.findEnrichment(new ScrapeRequest(mediaType, title, year, uniqueIds)).ifPresent(found::add);
            } catch (ScraperException e) {
                log.warn("Enrichment of \"{}\" not loaded from {}: {}", title, scraper.id(), e.getMessage());
            }
        }
        return found;
    }

    /**
     * Ob ein Anbieter etwas beitragen kann.
     *
     * @param refresh        vorhandene Bewertungen werden aktualisiert; dann zählt jeder Anbieter mit Bewertungen
     * @param presentRatings Kennungen der vorhandenen Bewertungen
     * @param emptyFields    ob Felder leer sind, die Anbieter mit Angaben füllen können
     */
    public static Predicate<EnrichmentScraper> contributes(boolean refresh, Set<String> presentRatings,
            boolean emptyFields) {
        return scraper -> refresh && !scraper.ratingSources().isEmpty() || !presentRatings.containsAll(scraper.ratingSources())
                || scraper.providesDetails() && emptyFields;
    }

    /**
     * Führt Bewertungen zusammen: Neue kommen hinten dazu, die erste wird Standard, wenn es noch keine gab. Vorhandene
     * Bewertungen derselben Kennung bleiben, außer {@code replace} ist gesetzt; dann bekommen sie die neuen Werte und
     * behalten ihre Standard-Markierung.
     *
     * @param <T> Bewertungstyp von Film oder Serie
     */
    public static <T> List<T> mergeRatings(List<T> current, List<Enrichment> enrichments, boolean replace,
            RatingAdapter<T> adapter) {
        List<T> merged = new ArrayList<>(current);
        for (Enrichment enrichment : enrichments) {
            for (ProviderRating rating : enrichment.ratings()) {
                int index = indexOf(merged, rating.source(), adapter);
                if (index < 0) {
                    merged.add(adapter.create(rating, merged.isEmpty()));
                } else if (replace) {
                    merged.set(index, adapter.create(rating, adapter.isDefault(merged.get(index))));
                }
            }
        }
        return merged;
    }

    private static <T> int indexOf(List<T> ratings, String source, RatingAdapter<T> adapter) {
        for (int index = 0; index < ratings.size(); index++) {
            if (adapter.source(ratings.get(index)).equalsIgnoreCase(source)) {
                return index;
            }
        }
        return -1;
    }

    /** Erster nicht leerer Wert der Ergänzungen. */
    public static <V> V first(List<Enrichment> enrichments, Function<Enrichment, V> value) {
        return enrichments.stream().map(value)
                .filter(candidate -> candidate != null && !(candidate instanceof List<?> list && list.isEmpty()))
                .findFirst().orElse(null);
    }

    /**
     * Übersetzt zwischen Anbieter-Bewertungen und den Bewertungen von Film bzw. Serie.
     *
     * @param <T> Bewertungstyp von Film oder Serie
     */
    public interface RatingAdapter<T> {

        String source(T rating);

        boolean isDefault(T rating);

        T create(ProviderRating rating, boolean defaultRating);
    }
}
