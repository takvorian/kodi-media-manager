package de.tiktak.kmm.movie;

import java.util.List;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.MetadataResult;
import de.tiktak.kmm.plugin.api.MetadataScraper;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.SearchQuery;
import lombok.RequiredArgsConstructor;

/**
 * Sucht Filme bei einem Metadaten-Scraper und bereitet die Daten eines gewählten Treffers als
 * Vorschlag auf. Gespeichert wird erst, wenn der Nutzer den Vorschlag übernimmt.
 */
@Service
@RequiredArgsConstructor
public class MovieScrapeService {

    private final ScraperRegistry scraperRegistry;
    private final MovieMetadataService metadataService;

    /**
     * @param title Suchbegriff; ohne Angabe der Titel des Films
     * @param year  Jahr; ohne Angabe das Jahr des Films, sofern der Titel nicht angegeben wurde
     */
    public List<ScrapeCandidate> search(long movieId, String scraperId, String title, Integer year) {
        MovieMetadata current = metadataService.findDetails(movieId).metadata();
        boolean customTitle = title != null && !title.isBlank();
        SearchQuery query = new SearchQuery(MediaType.MOVIE, customTitle ? title : current.title(),
                customTitle ? year : current.year());
        return call(scraper(scraperId), scraper -> scraper.search(query)).stream()
                .map(ScrapeCandidate::from)
                .toList();
    }

    /**
     * @return die vorhandenen Metadaten, überlagert mit den Daten des Treffers
     */
    public MovieMetadata proposal(long movieId, String scraperId, String providerId) {
        MovieMetadata current = metadataService.findDetails(movieId).metadata();
        MetadataScraper scraper = scraper(scraperId);
        MetadataResult scraped = call(scraper, s -> s.fetch(providerId))
                .orElseThrow(() -> ScraperProblemException.notFoundAtProvider(scraper, providerId));
        return ScrapedMetadataMerger.merge(current, scraped);
    }

    private MetadataScraper scraper(String scraperId) {
        MetadataScraper scraper = scraperRegistry.metadataScraper(scraperId)
                .orElseThrow(() -> ScraperProblemException.unknown(scraperId));
        scraper.missingConfiguration().ifPresent(hint -> {
            throw ScraperProblemException.notConfigured(scraper, hint);
        });
        return scraper;
    }

    private static <T> T call(MetadataScraper scraper, Function<MetadataScraper, T> request) {
        try {
            return request.apply(scraper);
        } catch (ScraperException e) {
            throw ScraperProblemException.providerFailed(scraper, e);
        }
    }
}
