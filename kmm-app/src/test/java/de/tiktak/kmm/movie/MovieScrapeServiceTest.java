package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.MetadataResult;
import de.tiktak.kmm.plugin.api.MetadataScraper;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.SearchQuery;
import de.tiktak.kmm.plugin.api.SearchResult;
import de.tiktak.kmm.scanner.DiscFormat;

@ExtendWith(MockitoExtension.class)
class MovieScrapeServiceTest {

    private static final long MOVIE_ID = 5L;
    private static final MovieMetadata CURRENT = new MovieMetadata("Heat", null, null, 1995, null, null, null, null,
            null, null, List.of(), null, null);

    @Mock
    private ScraperRegistry scraperRegistry;

    @Mock
    private MovieMetadataService metadataService;

    @Mock
    private MetadataScraper tmdb;

    private MovieScrapeService service;

    @BeforeEach
    void setUp() {
        service = new MovieScrapeService(scraperRegistry, metadataService, new ScrapingProperties("tmdb"));
        MovieDetails details = new MovieDetails(MOVIE_ID, 1L, "/media/Heat.mkv", DiscFormat.FILE, 1, null, CURRENT,
                MovieSupplement.EMPTY, new MovieDetails.NfoInfo("/media/Heat.nfo", false, null));
        lenient().when(metadataService.findDetails(MOVIE_ID)).thenReturn(details);
        lenient().when(tmdb.displayName()).thenReturn("TMDB");
    }

    @Test
    void searchesWithTitleAndYearOfTheMovieByDefault() {
        givenScraper();
        when(tmdb.search(new SearchQuery(MediaType.MOVIE, "Heat", 1995))).thenReturn(List.of(
                new SearchResult("949", "Heat", "Heat", 1995, "Handlung", URI.create("https://img/heat.jpg"))));

        assertThat(service.search(MOVIE_ID, "tmdb", null, null)).containsExactly(
                new ScrapeCandidate("949", "Heat", "Heat", 1995, "Handlung", URI.create("https://img/heat.jpg")));
    }

    @Test
    void searchesWithCustomTitleAndOnlyTheGivenYear() {
        givenScraper();
        when(tmdb.search(new SearchQuery(MediaType.MOVIE, "Heat 2", null))).thenReturn(List.of());

        assertThat(service.search(MOVIE_ID, "tmdb", " Heat 2 ", null)).isEmpty();
    }

    @Test
    void proposesCurrentMetadataOverlaidWithScrapedData() {
        givenScraper();
        when(tmdb.fetch("949")).thenReturn(Optional.of(MetadataResult.builder().title("Heat").runtime(170).build()));

        MovieMetadata proposal = service.proposal(MOVIE_ID, "tmdb", "949");

        assertThat(proposal.runtime()).isEqualTo(170);
        assertThat(proposal.year()).isEqualTo(1995);
    }

    @Test
    void rejectsUnknownProviderEntry() {
        givenScraper();
        when(tmdb.fetch("0")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.proposal(MOVIE_ID, "tmdb", "0"))
                .isInstanceOfSatisfying(ScraperProblemException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void rejectsUnknownScraper() {
        when(scraperRegistry.metadataScraper("imdb")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.search(MOVIE_ID, "imdb", null, null))
                .isInstanceOfSatisfying(ScraperProblemException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getBody().getDetail()).contains("imdb");
                });
    }

    @Test
    void rejectsScraperWithMissingConfigurationWithoutCallingIt() {
        when(scraperRegistry.metadataScraper("tmdb")).thenReturn(Optional.of(tmdb));
        when(tmdb.missingConfiguration()).thenReturn(Optional.of("API-Key fehlt"));

        assertThatThrownBy(() -> service.search(MOVIE_ID, "tmdb", null, null))
                .isInstanceOfSatisfying(ScraperProblemException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(exception.getBody().getDetail()).isEqualTo("API-Key fehlt");
                    assertThat(exception.getBody().getTitle()).isEqualTo("TMDB ist nicht eingerichtet");
                });
        verify(tmdb, never()).search(any());
    }

    @Test
    void reportsProviderFailureAsBadGateway() {
        givenScraper();
        when(tmdb.search(any())).thenThrow(new ScraperException("TMDB ist nicht erreichbar"));

        assertThatThrownBy(() -> service.search(MOVIE_ID, "tmdb", null, null))
                .isInstanceOfSatisfying(ScraperProblemException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.getBody().getDetail()).isEqualTo("TMDB ist nicht erreichbar");
                });
    }

    @Test
    void autoScrapeLeavesIdentifiedMoviesUntouched() {
        givenMovie(new MovieMetadata("Heat", null, null, 1995, null, null, null, null, null, null, List.of(), null,
                "949"));

        assertThat(service.scrapeMissingMetadata(MOVIE_ID)).isEqualTo(MetadataScrapeOutcome.ALREADY_IDENTIFIED);
        verify(scraperRegistry, never()).metadataScrapersFor(any());
    }

    @Test
    void autoScrapeWritesSafeMatchOfThePreferredScraper() {
        MetadataScraper other = readyScraper("other");
        MetadataScraper preferred = readyScraper("tmdb");
        when(scraperRegistry.metadataScrapersFor(MediaType.MOVIE)).thenReturn(List.of(other, preferred));
        when(preferred.scrape(new ScrapeRequest(MediaType.MOVIE, "Heat", 1995, Map.of())))
                .thenReturn(Optional.of(MetadataResult.builder().title("Heat").runtime(170)
                        .uniqueIds(Map.of("tmdb", "949")).build()));

        assertThat(service.scrapeMissingMetadata(MOVIE_ID)).isEqualTo(MetadataScrapeOutcome.SCRAPED);

        ArgumentCaptor<MovieMetadata> saved = ArgumentCaptor.forClass(MovieMetadata.class);
        verify(metadataService).update(eq(MOVIE_ID), saved.capture());
        assertThat(saved.getValue().runtime()).isEqualTo(170);
        assertThat(saved.getValue().tmdbId()).isEqualTo("949");
        verify(other, never()).scrape(any());
    }

    @Test
    void autoScrapeFallsBackToFirstReadyScraperAndPassesKnownImdbId() {
        givenMovie(new MovieMetadata("Heat", null, null, 1995, null, null, null, null, null, null, List.of(),
                "tt0113277", null));
        MetadataScraper unconfigured = readyScraper("tmdb");
        when(unconfigured.missingConfiguration()).thenReturn(Optional.of("Key fehlt"));
        MetadataScraper other = readyScraper("other");
        when(scraperRegistry.metadataScrapersFor(MediaType.MOVIE)).thenReturn(List.of(unconfigured, other));
        when(other.scrape(new ScrapeRequest(MediaType.MOVIE, "Heat", 1995, Map.of("imdb", "tt0113277"))))
                .thenReturn(Optional.empty());

        assertThat(service.scrapeMissingMetadata(MOVIE_ID)).isEqualTo(MetadataScrapeOutcome.NO_MATCH);
        verify(metadataService, never()).update(anyLong(), any());
    }

    @Test
    void autoScrapeReportsMissingScraper() {
        when(scraperRegistry.metadataScrapersFor(MediaType.MOVIE)).thenReturn(List.of());

        assertThat(service.scrapeMissingMetadata(MOVIE_ID)).isEqualTo(MetadataScrapeOutcome.NO_SCRAPER);
    }

    @Test
    void autoScrapeReportsProviderFailure() {
        MetadataScraper preferred = readyScraper("tmdb");
        when(scraperRegistry.metadataScrapersFor(MediaType.MOVIE)).thenReturn(List.of(preferred));
        when(preferred.scrape(any())).thenThrow(new ScraperException("Anfragelimit erreicht"));

        assertThatThrownBy(() -> service.scrapeMissingMetadata(MOVIE_ID))
                .isInstanceOfSatisfying(ScraperProblemException.class,
                        exception -> assertThat(exception.getBody().getDetail()).isEqualTo("Anfragelimit erreicht"));
    }

    private void givenMovie(MovieMetadata metadata) {
        when(metadataService.findDetails(MOVIE_ID)).thenReturn(new MovieDetails(MOVIE_ID, 1L, "/media/Heat.mkv",
                DiscFormat.FILE, 1, null, metadata, MovieSupplement.EMPTY, new MovieDetails.NfoInfo("/media/Heat.nfo", false, null)));
    }

    private static MetadataScraper readyScraper(String id) {
        MetadataScraper scraper = mock(MetadataScraper.class);
        lenient().when(scraper.id()).thenReturn(id);
        lenient().when(scraper.displayName()).thenReturn(id.toUpperCase());
        lenient().when(scraper.missingConfiguration()).thenReturn(Optional.empty());
        return scraper;
    }

    private void givenScraper() {
        when(scraperRegistry.metadataScraper("tmdb")).thenReturn(Optional.of(tmdb));
        when(tmdb.missingConfiguration()).thenReturn(Optional.empty());
    }
}
