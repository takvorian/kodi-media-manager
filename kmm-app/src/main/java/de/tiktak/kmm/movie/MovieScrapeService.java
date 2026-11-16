package de.tiktak.kmm.movie;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.plugin.ScrapeLanguage;
import de.tiktak.kmm.plugin.ScraperChoice;
import de.tiktak.kmm.plugin.ScraperInfo;
import de.tiktak.kmm.plugin.ScraperKind;
import de.tiktak.kmm.plugin.ScraperPriorityService;
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
 * Searches for movies with a metadata scraper and prepares the data of a chosen match as a
 * suggestion. Nothing is saved until the user accepts the suggestion.
 */
@Service
@RequiredArgsConstructor
public class MovieScrapeService {

    private static final String IMDB = "imdb";
    private static final String TMDB = "tmdb";

    private final ScraperRegistry scraperRegistry;
    private final MovieMetadataService metadataService;
    private final ScraperPriorityService priorityService;
    private final SettingsService settingsService;
    private final MovieCollectionAssigner collectionAssigner;

    /**
     * @param title search term; if omitted, the movie's title
     * @param year  year; if omitted, the movie's year, provided no title was given
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
     * @return the existing metadata, overlaid with the data of the match, and the movie's collection
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
     * Assigns a scraped movie to the set of its collection, provided "Assign movies to their movie set when scraping"
     * is enabled.
     *
     * @return whether the set membership has changed
     */
    public boolean assignCollection(long movieId, CollectionReference collection) {
        return settingsService.current().movieSets().assignOnScrape()
                && collectionAssigner.assign(movieId, collection);
    }

    /**
     * Fetches metadata for a movie that has no TMDB ID yet. Only a reliable match from the preferred
     * scraper is applied; existing values the scraper does not know are kept.
     *
     * @throws ScraperProblemException if the provider reports an error
     */
    public MetadataScrapeOutcome scrapeMissingMetadata(long movieId) {
        MovieDetails details = metadataService.findDetails(movieId);
        MovieMetadata current = details.metadata();
        if (current.tmdbId() != null) {
            return MetadataScrapeOutcome.ALREADY_IDENTIFIED;
        }
        return inLanguageOf(movieId, () -> {
            List<MetadataScraper> scrapers = automaticScrapers(movieId, details.sourceId());
            if (scrapers.isEmpty()) {
                return MetadataScrapeOutcome.NO_SCRAPER;
            }
            Map<String, String> knownIds = current.imdbId() == null ? Map.of() : Map.of(IMDB, current.imdbId());
            ScrapeRequest request = new ScrapeRequest(MediaType.MOVIE, MovieSearchTitle.of(current),
                    MovieSearchTitle.yearOf(current), knownIds);
            Optional<MetadataResult> scraped = Optional.empty();
            for (int index = 0; index < scrapers.size() && scraped.isEmpty(); index++) {
                scraped = call(scrapers.get(index), s -> s.scrape(request));
            }
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
     * Fetches the movie's title and texts again in its language and overwrites the existing ones, including those
     * edited by hand. Whatever the provider does not have in this language is kept.
     *
     * @return whether anything has changed
     * @throws ScraperProblemException if no provider is set up, it reports an error or cannot find the movie
     *                                 unambiguously
     */
    public boolean refetchTexts(long movieId) {
        MovieDetails details = metadataService.findDetails(movieId);
        MovieMetadata current = details.metadata();
        return inLanguageOf(movieId, () -> {
            MetadataScraper scraper = automaticScrapers(movieId, details.sourceId()).stream().findFirst()
                    .orElseThrow(ScraperProblemException::noMetadataScraper);
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

    /**
     * The scrapers to try one after the other: only the one chosen for the movie, otherwise those set up in the
     * order of the movie's source.
     *
     * @throws ScraperProblemException if the scraper chosen for the movie is gone or misses its configuration
     */
    private List<MetadataScraper> automaticScrapers(long movieId, long sourceId) {
        return metadataService.ownScraper(movieId)
                .map(own -> List.of(scraper(own)))
                .orElseGet(() -> readyScrapers(sourceId));
    }

    /** The movie scrapers that are set up, in the order of the movie's source. */
    private List<MetadataScraper> readyScrapers(long sourceId) {
        return orderedScrapers(sourceId).stream()
                .filter(scraper -> scraper.missingConfiguration().isEmpty())
                .toList();
    }

    private List<MetadataScraper> orderedScrapers(long sourceId) {
        return priorityService.ordered(ScraperKind.METADATA, MediaType.MOVIE, sourceId,
                scraperRegistry.metadataScrapersFor(MediaType.MOVIE));
    }

    /** The movie's metadata scraper with the scrapers it can be switched to. */
    public ScraperChoice scraperChoice(long movieId) {
        MovieDetails details = metadataService.findDetails(movieId);
        return new ScraperChoice(metadataService.ownScraper(movieId).orElse(null),
                readyScrapers(details.sourceId()).stream().findFirst().map(MetadataScraper::id).orElse(null),
                orderedScrapers(details.sourceId()).stream().map(ScraperInfo::from).toList());
    }

    /**
     * Checks that the scraper exists and is set up before a movie gets it.
     *
     * @throws ScraperProblemException if it is unknown or misses its configuration
     */
    void requireScraper(String scraperId) {
        scraper(scraperId);
    }

    /**
     * After taking over a match: the movie keeps the scraper it came from if that is not the one of its source
     * anyway, so later scraping uses the same provider.
     */
    public void rememberScraper(long movieId, String scraperId) {
        scraper(scraperId);
        long sourceId = metadataService.findDetails(movieId).sourceId();
        boolean fromSource = readyScrapers(sourceId).stream().findFirst()
                .map(scraper -> scraper.id().equals(scraperId)).orElse(false);
        metadataService.changeScraper(movieId, fromSource ? null : scraperId);
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
