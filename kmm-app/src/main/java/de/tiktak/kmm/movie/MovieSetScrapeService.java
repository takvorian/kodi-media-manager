package de.tiktak.kmm.movie;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.MovieSetResult;
import de.tiktak.kmm.plugin.api.MovieSetScraper;
import de.tiktak.kmm.plugin.api.ScraperException;
import lombok.RequiredArgsConstructor;

/**
 * Searches movie sets at an online service (TMDB collections) and takes over their title and description, like
 * Ember's "MovieSetScraperTitle" and "MovieSetScraperPlot". The first scraper that is set up is queried.
 */
@Service
@RequiredArgsConstructor
public class MovieSetScrapeService {

    private final ScraperRegistry scraperRegistry;
    private final MovieSetService setService;

    /**
     * @throws ScraperProblemException if no scraper is available or the provider reports an error
     */
    public List<ScrapeCandidate> search(String title) {
        return call(scraper(), scraper -> scraper.searchSets(title)).stream()
                .map(ScrapeCandidate::from)
                .toList();
    }

    /**
     * @return the set's details with title, description and ID of the match; a description is kept if the
     *         provider does not know one
     */
    public MovieSetData proposal(long setId, String providerId) {
        MovieSetData current = setService.find(setId).data();
        MovieSetScraper scraper = scraper();
        MovieSetResult scraped = call(scraper, s -> s.fetchSet(providerId))
                .orElseThrow(() -> ScraperProblemException.notFoundAtProvider(scraper, providerId));
        return new MovieSetData(scraped.title(), scraped.overview() != null ? scraped.overview() : current.overview(),
                scraped.providerId(), current.sortMethod());
    }

    /** The details of a collection, empty if the provider does not know it. */
    Optional<MovieSetResult> fetch(String providerId) {
        return call(scraper(), scraper -> scraper.fetchSet(providerId));
    }

    private MovieSetScraper scraper() {
        List<MovieSetScraper> scrapers = scraperRegistry.movieSetScrapers();
        if (scrapers.isEmpty()) {
            throw ScraperProblemException.noMovieSetScraper();
        }
        return scrapers.stream()
                .filter(scraper -> scraper.missingConfiguration().isEmpty())
                .findFirst()
                .orElseThrow(() -> ScraperProblemException.notConfigured(scrapers.getFirst(),
                        scrapers.getFirst().missingConfiguration().orElseThrow()));
    }

    private static <T> T call(MovieSetScraper scraper, Function<MovieSetScraper, T> request) {
        try {
            return request.apply(scraper);
        } catch (ScraperException e) {
            throw ScraperProblemException.providerFailed(scraper, e);
        }
    }
}
