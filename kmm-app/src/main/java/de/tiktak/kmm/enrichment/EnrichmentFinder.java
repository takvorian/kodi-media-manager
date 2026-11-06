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
 * Fetches supplements (ratings, details for empty fields) from the supplementing scrapers, such as Trakt and
 * OMDb. Providers that are not set up or not reachable are skipped.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnrichmentFinder {

    private final ScraperRegistry scraperRegistry;

    /**
     * @param uniqueIds known IDs per provider; without IDs nothing is searched
     * @param wanted    which providers are asked, e.g. only those with missing ratings
     * @return the supplements in the order of the providers
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
     * Whether a provider can contribute anything.
     *
     * @param refresh        existing ratings are updated; then every provider with ratings counts
     * @param presentRatings identifiers of the existing ratings
     * @param emptyFields    whether fields are empty that providers can fill with details
     */
    public static Predicate<EnrichmentScraper> contributes(boolean refresh, Set<String> presentRatings,
            boolean emptyFields) {
        return scraper -> refresh && !scraper.ratingSources().isEmpty() || !presentRatings.containsAll(scraper.ratingSources())
                || scraper.providesDetails() && emptyFields;
    }

    /**
     * Merges ratings: new ones are appended, the first becomes the default if there was none yet. Existing
     * ratings with the same identifier stay, unless {@code replace} is set; then they get the new values and
     * keep their default flag.
     *
     * @param <T> rating type of movie or TV show
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

    /** First non-empty value of the supplements. */
    public static <V> V first(List<Enrichment> enrichments, Function<Enrichment, V> value) {
        return enrichments.stream().map(value)
                .filter(candidate -> candidate != null && !(candidate instanceof List<?> list && list.isEmpty()))
                .findFirst().orElse(null);
    }

    /**
     * Translates between provider ratings and the ratings of a movie or TV show.
     *
     * @param <T> rating type of movie or TV show
     */
    public interface RatingAdapter<T> {

        String source(T rating);

        boolean isDefault(T rating);

        T create(ProviderRating rating, boolean defaultRating);
    }
}
