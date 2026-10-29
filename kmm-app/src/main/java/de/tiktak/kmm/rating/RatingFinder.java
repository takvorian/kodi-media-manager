package de.tiktak.kmm.rating;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ProviderRating;
import de.tiktak.kmm.plugin.api.RatingScraper;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Holt Bewertungen weiterer Anbieter (Bewertungs-Scraper, etwa Trakt), die einem Film oder einer Serie noch fehlen.
 * Nicht eingerichtete oder nicht erreichbare Anbieter werden übergangen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RatingFinder {

    private final ScraperRegistry scraperRegistry;

    /**
     * @param uniqueIds bekannte IDs je Anbieter; ohne IDs wird nicht gesucht
     * @param present   Kennungen der Bewertungen, die schon vorliegen
     * @return die fehlenden Bewertungen, die die Anbieter liefern
     */
    public List<ProviderRating> findMissing(MediaType mediaType, String title, Integer year,
            Map<String, String> uniqueIds, Set<String> present) {
        if (uniqueIds.isEmpty()) {
            return List.of();
        }
        List<ProviderRating> found = new ArrayList<>();
        for (RatingScraper scraper : scraperRegistry.ratingScrapersFor(mediaType)) {
            if (present.contains(scraper.ratingSource()) || scraper.missingConfiguration().isPresent()) {
                continue;
            }
            try {
                scraper.findRating(new ScrapeRequest(mediaType, title, year, uniqueIds)).ifPresent(found::add);
            } catch (ScraperException e) {
                log.warn("Rating of \"{}\" not loaded from {}: {}", title, scraper.id(), e.getMessage());
            }
        }
        return found;
    }
}
