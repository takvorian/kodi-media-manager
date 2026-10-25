package de.tiktak.kmm.movie;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.MetadataResult;
import de.tiktak.kmm.plugin.api.MetadataScraper;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
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

    private static final String IMDB = "imdb";

    private final ScraperRegistry scraperRegistry;
    private final MovieMetadataService metadataService;
    private final ScrapingProperties properties;

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

    /**
     * Holt Metadaten für einen Film, der noch keine TMDB-ID hat. Übernommen wird nur ein sicherer
     * Treffer des bevorzugten Scrapers; vorhandene Werte, die der Scraper nicht kennt, bleiben.
     *
     * @throws ScraperProblemException wenn der Anbieter einen Fehler meldet
     */
    public MetadataScrapeOutcome scrapeMissingMetadata(long movieId) {
        MovieMetadata current = metadataService.findDetails(movieId).metadata();
        if (current.tmdbId() != null) {
            return MetadataScrapeOutcome.ALREADY_IDENTIFIED;
        }
        Optional<MetadataScraper> scraper = preferredReadyScraper();
        if (scraper.isEmpty()) {
            return MetadataScrapeOutcome.NO_SCRAPER;
        }
        Map<String, String> knownIds = current.imdbId() == null ? Map.of() : Map.of(IMDB, current.imdbId());
        ScrapeRequest request = new ScrapeRequest(MediaType.MOVIE, current.title(), current.year(), knownIds);
        Optional<MetadataResult> scraped = call(scraper.get(), s -> s.scrape(request));
        if (scraped.isEmpty()) {
            return MetadataScrapeOutcome.NO_MATCH;
        }
        metadataService.update(movieId, ScrapedMetadataMerger.merge(current, scraped.get()));
        return MetadataScrapeOutcome.SCRAPED;
    }

    /** Der konfigurierte Scraper, sonst der erste eingerichtete. */
    private Optional<MetadataScraper> preferredReadyScraper() {
        List<MetadataScraper> ready = scraperRegistry.metadataScrapersFor(MediaType.MOVIE).stream()
                .filter(scraper -> scraper.missingConfiguration().isEmpty())
                .toList();
        return ready.stream()
                .filter(scraper -> scraper.id().equals(properties.preferredMetadataScraper()))
                .findFirst()
                .or(() -> ready.stream().findFirst());
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
