package de.tiktak.kmm.movie;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.plugin.ScrapeLanguage;
import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.CollectionReference;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.MetadataResult;
import de.tiktak.kmm.plugin.api.MetadataScraper;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.SearchQuery;
import de.tiktak.kmm.settings.SettingsService;
import lombok.RequiredArgsConstructor;

/**
 * Sucht Filme bei einem Metadaten-Scraper und bereitet die Daten eines gewählten Treffers als
 * Vorschlag auf. Gespeichert wird erst, wenn der Nutzer den Vorschlag übernimmt.
 */
@Service
@RequiredArgsConstructor
public class MovieScrapeService {

    private static final String IMDB = "imdb";
    private static final String TMDB = "tmdb";

    private final ScraperRegistry scraperRegistry;
    private final MovieMetadataService metadataService;
    private final ScrapingProperties properties;
    private final SettingsService settingsService;
    private final MovieCollectionAssigner collectionAssigner;

    /**
     * @param title Suchbegriff; ohne Angabe der Titel des Films
     * @param year  Jahr; ohne Angabe das Jahr des Films, sofern der Titel nicht angegeben wurde
     */
    public List<ScrapeCandidate> search(long movieId, String scraperId, String title, Integer year) {
        MovieMetadata current = metadataService.findDetails(movieId).metadata();
        boolean customTitle = title != null && !title.isBlank() && !title.strip().equals(current.title());
        SearchQuery query = new SearchQuery(MediaType.MOVIE, customTitle ? title : MovieSearchTitle.of(current),
                customTitle ? year : MovieSearchTitle.yearOf(current));
        return inLanguageOf(movieId, () -> call(scraper(scraperId), scraper -> scraper.search(query)).stream()
                .map(ScrapeCandidate::from)
                .toList());
    }

    /**
     * @return die vorhandenen Metadaten, überlagert mit den Daten des Treffers, und die Sammlung des Films
     */
    public MovieScrapeProposal proposal(long movieId, String scraperId, String providerId) {
        MovieMetadata current = metadataService.findDetails(movieId).metadata();
        return inLanguageOf(movieId, () -> {
            MetadataScraper scraper = scraper(scraperId);
            MetadataResult scraped = call(scraper, s -> s.fetch(providerId))
                    .orElseThrow(() -> ScraperProblemException.notFoundAtProvider(scraper, providerId));
            return new MovieScrapeProposal(ScrapedMetadataMerger.merge(current, scraped), scraped.collection());
        });
    }

    /**
     * Ordnet einen gescrapten Film dem Set seiner Sammlung zu, sofern „Filme beim Scrapen ihrem Filmset zuordnen“
     * eingeschaltet ist.
     *
     * @return ob sich die Set-Zugehörigkeit geändert hat
     */
    public boolean assignCollection(long movieId, CollectionReference collection) {
        return settingsService.current().movieSets().assignOnScrape()
                && collectionAssigner.assign(movieId, collection);
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
        return inLanguageOf(movieId, () -> {
            Optional<MetadataScraper> scraper = preferredReadyScraper();
            if (scraper.isEmpty()) {
                return MetadataScrapeOutcome.NO_SCRAPER;
            }
            Map<String, String> knownIds = current.imdbId() == null ? Map.of() : Map.of(IMDB, current.imdbId());
            ScrapeRequest request = new ScrapeRequest(MediaType.MOVIE, MovieSearchTitle.of(current),
                    MovieSearchTitle.yearOf(current), knownIds);
            Optional<MetadataResult> scraped = call(scraper.get(), s -> s.scrape(request));
            if (scraped.isEmpty()) {
                return MetadataScrapeOutcome.NO_MATCH;
            }
            metadataService.update(movieId, ScrapedMetadataMerger.merge(current, scraped.get()));
            metadataService.storeLanguageInNfo(movieId);
            if (scraped.get().collection() != null) {
                assignCollection(movieId, scraped.get().collection());
            }
            return MetadataScrapeOutcome.SCRAPED;
        });
    }

    /**
     * Holt Titel und Texte des Films neu in seiner Sprache und überschreibt die vorhandenen, auch von Hand geänderte.
     * Was der Anbieter in dieser Sprache nicht hat, bleibt.
     *
     * @return ob sich etwas geändert hat
     * @throws ScraperProblemException wenn kein Anbieter eingerichtet ist, er einen Fehler meldet oder den Film nicht
     *                                 eindeutig findet
     */
    public boolean refetchTexts(long movieId) {
        MovieMetadata current = metadataService.findDetails(movieId).metadata();
        return inLanguageOf(movieId, () -> {
            MetadataScraper scraper = preferredReadyScraper().orElseThrow(ScraperProblemException::noMetadataScraper);
            Map<String, String> knownIds = new HashMap<>();
            Optional.ofNullable(current.imdbId()).ifPresent(id -> knownIds.put(IMDB, id));
            Optional.ofNullable(current.tmdbId()).ifPresent(id -> knownIds.put(TMDB, id));
            ScrapeRequest request = new ScrapeRequest(MediaType.MOVIE, MovieSearchTitle.of(current),
                    MovieSearchTitle.yearOf(current), knownIds);
            MetadataResult scraped = call(scraper, s -> s.scrape(request))
                    .orElseThrow(() -> ScraperProblemException.notIdentified(scraper, current.title()));
            MovieMetadata merged = ScrapedMetadataMerger.merge(current, scraped);
            if (merged.equals(current)) {
                return false;
            }
            metadataService.update(movieId, merged);
            metadataService.storeLanguageInNfo(movieId);
            return true;
        });
    }

    private <T> T inLanguageOf(long movieId, Supplier<T> body) {
        return ScrapeLanguage.with(metadataService.scrapeLanguage(movieId), body);
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
