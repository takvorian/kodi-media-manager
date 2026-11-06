package de.tiktak.kmm.movie;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.i18n.Messages;
import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.MovieSetResult;
import de.tiktak.kmm.plugin.api.MovieSetScraper;
import de.tiktak.kmm.plugin.api.ScraperException;
import lombok.RequiredArgsConstructor;

/**
 * Filmsets bei einem Online-Dienst (TMDB-Sammlungen) suchen und deren Titel und Beschreibung übernehmen, wie
 * Embers „MovieSetScraperTitle“ und „MovieSetScraperPlot“. Gefragt wird der erste eingerichtete Scraper.
 */
@Service
@RequiredArgsConstructor
public class MovieSetScrapeService {

    private final ScraperRegistry scraperRegistry;
    private final MovieSetService setService;

    /**
     * @throws ScraperProblemException wenn kein Scraper bereitsteht oder der Anbieter einen Fehler meldet
     */
    public List<ScrapeCandidate> search(String title) {
        return call(scraper(), scraper -> scraper.searchSets(title)).stream()
                .map(ScrapeCandidate::from)
                .toList();
    }

    /**
     * @return die Angaben des Sets mit Titel, Beschreibung und ID des Treffers; eine Beschreibung bleibt, wenn der
     *         Anbieter keine kennt
     */
    public MovieSetData proposal(long setId, String providerId) {
        MovieSetData current = setService.find(setId).data();
        MovieSetScraper scraper = scraper();
        MovieSetResult scraped = call(scraper, s -> s.fetchSet(providerId))
                .orElseThrow(() -> ScraperProblemException.notFoundAtProvider(scraper, providerId));
        return new MovieSetData(scraped.title(), scraped.overview() != null ? scraped.overview() : current.overview(),
                scraped.providerId(), current.sortMethod());
    }

    /** Die Angaben einer Sammlung, leer, wenn der Anbieter sie nicht kennt. */
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
                        scrapers.getFirst().missingConfiguration().map(Messages::english).orElseThrow()));
    }

    private static <T> T call(MovieSetScraper scraper, Function<MovieSetScraper, T> request) {
        try {
            return request.apply(scraper);
        } catch (ScraperException e) {
            throw ScraperProblemException.providerFailed(scraper, e);
        }
    }
}
