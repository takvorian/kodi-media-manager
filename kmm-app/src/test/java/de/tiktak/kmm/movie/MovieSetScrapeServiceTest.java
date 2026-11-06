package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.MovieSetResult;
import de.tiktak.kmm.plugin.api.MovieSetScraper;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.SearchResult;

@ExtendWith(MockitoExtension.class)
class MovieSetScrapeServiceTest {

    private static final long SET_ID = 7L;
    private static final URI POSTER = URI.create("https://image.tmdb.org/t/p/w185/matrix.jpg");

    @Mock
    private ScraperRegistry scraperRegistry;

    @Mock
    private MovieSetService setService;

    @InjectMocks
    private MovieSetScrapeService service;

    @Test
    void searchesCollectionsWithTheFirstReadyScraper() {
        MovieSetScraper unconfigured = scraper(Optional.of("Key fehlt"));
        MovieSetScraper tmdb = scraper(Optional.empty());
        when(scraperRegistry.movieSetScrapers()).thenReturn(List.of(unconfigured, tmdb));
        when(tmdb.searchSets("Matrix")).thenReturn(List.of(
                new SearchResult("2344", "Matrix Filmreihe", "The Matrix Collection", null, "Neo.", POSTER)));

        assertThat(service.search("Matrix")).containsExactly(
                new ScrapeCandidate("2344", "Matrix Filmreihe", "The Matrix Collection", null, "Neo.", POSTER));
        verify(unconfigured, never()).searchSets("Matrix");
    }

    @Test
    void proposesTitleOverviewAndIdOfTheMatchAndKeepsTheSortMethod() {
        givenSet("Matrix", "Alte Beschreibung");
        MovieSetScraper tmdb = givenReadyScraper();
        when(tmdb.fetchSet("2344")).thenReturn(Optional.of(new MovieSetResult("2344", "Matrix Filmreihe", "Neo.")));

        assertThat(service.proposal(SET_ID, "2344"))
                .isEqualTo(new MovieSetData("Matrix Filmreihe", "Neo.", "2344", SetSortMethod.TITLE));
    }

    @Test
    void keepsTheOverviewIfTheMatchHasNone() {
        givenSet("Matrix", "Alte Beschreibung");
        MovieSetScraper tmdb = givenReadyScraper();
        when(tmdb.fetchSet("2344")).thenReturn(Optional.of(new MovieSetResult("2344", "Matrix Filmreihe", null)));

        assertThat(service.proposal(SET_ID, "2344").overview()).isEqualTo("Alte Beschreibung");
    }

    @Test
    void reportsAnUnknownCollection() {
        givenSet("Matrix", null);
        MovieSetScraper tmdb = givenReadyScraper();
        when(tmdb.fetchSet("1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.proposal(SET_ID, "1")).isInstanceOfSatisfying(ScraperProblemException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void reportsMissingOrUnconfiguredScrapers() {
        when(scraperRegistry.movieSetScrapers()).thenReturn(List.of());
        assertThatThrownBy(() -> service.search("Matrix")).isInstanceOfSatisfying(ScraperProblemException.class,
                e -> assertThat(e.getBody().getTitle()).isEqualTo("No scraper for movie sets"));

        MovieSetScraper unconfigured = scraper(Optional.of("Bitte den TMDB-API-Key eintragen."));
        when(scraperRegistry.movieSetScrapers()).thenReturn(List.of(unconfigured));
        assertThatThrownBy(() -> service.search("Matrix")).isInstanceOfSatisfying(ScraperProblemException.class,
                e -> assertThat(e.getBody().getDetail()).isEqualTo("Bitte den TMDB-API-Key eintragen."));
    }

    @Test
    void reportsProviderFailuresAsBadGateway() {
        MovieSetScraper tmdb = givenReadyScraper();
        when(tmdb.fetchSet("2344")).thenThrow(new ScraperException("Anfragelimit erreicht"));

        assertThatThrownBy(() -> service.fetch("2344")).isInstanceOfSatisfying(ScraperProblemException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    private void givenSet(String name, String overview) {
        when(setService.find(SET_ID)).thenReturn(new MovieSetDetails(SET_ID,
                new MovieSetData(name, overview, null, SetSortMethod.TITLE), List.of()));
    }

    private MovieSetScraper givenReadyScraper() {
        MovieSetScraper tmdb = scraper(Optional.empty());
        when(scraperRegistry.movieSetScrapers()).thenReturn(List.of(tmdb));
        return tmdb;
    }

    private static MovieSetScraper scraper(Optional<String> missingConfiguration) {
        MovieSetScraper scraper = mock(MovieSetScraper.class);
        lenient().when(scraper.displayName()).thenReturn("TMDB");
        lenient().when(scraper.missingConfiguration()).thenReturn(missingConfiguration);
        return scraper;
    }
}
