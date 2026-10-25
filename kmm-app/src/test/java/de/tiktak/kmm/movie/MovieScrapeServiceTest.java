package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.MetadataResult;
import de.tiktak.kmm.plugin.api.MetadataScraper;
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

    @InjectMocks
    private MovieScrapeService service;

    @BeforeEach
    void setUp() {
        MovieDetails details = new MovieDetails(MOVIE_ID, 1L, "/media/Heat.mkv", DiscFormat.FILE, 1, null, CURRENT,
                new MovieDetails.NfoInfo("/media/Heat.nfo", false, null));
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

    private void givenScraper() {
        when(scraperRegistry.metadataScraper("tmdb")).thenReturn(Optional.of(tmdb));
        when(tmdb.missingConfiguration()).thenReturn(Optional.empty());
    }
}
