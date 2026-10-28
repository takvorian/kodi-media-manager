package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.springframework.test.util.ReflectionTestUtils;

import de.tiktak.kmm.nfo.MovieNfoSupplement;
import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.TrailerCandidate;
import de.tiktak.kmm.plugin.api.TrailerScraper;
import de.tiktak.kmm.scanner.DiscFormat;
import de.tiktak.kmm.settings.KmmSettings;
import de.tiktak.kmm.settings.SettingsService;

@ExtendWith(MockitoExtension.class)
class MovieTrailerServiceTest {

    private static final long MOVIE_ID = 7L;
    private static final String GERMAN_TRAILER = "plugin://plugin.video.youtube/?action=play_video&videoid=de1";

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private MovieMetadataService metadataService;

    @Mock
    private MovieNfoSync nfoSync;

    @Mock
    private MovieSupplementWriter supplementWriter;

    @Mock
    private ScraperRegistry scraperRegistry;

    @Mock
    private SettingsService settingsService;

    @Mock
    private TrailerScraper scraper;

    private MovieTrailerService service;
    private Movie movie;

    @BeforeEach
    void setUp() {
        service = new MovieTrailerService(movieRepository, metadataService, nfoSync, supplementWriter,
                scraperRegistry, settingsService);
        movie = new Movie(1L, "/filme/Heat.mkv", "Heat", 1995, DiscFormat.FILE, 1);
        ReflectionTestUtils.setField(movie, "id", MOVIE_ID);
        lenient().when(movieRepository.findById(MOVIE_ID)).thenReturn(Optional.of(movie));
        lenient().when(metadataService.findDetails(MOVIE_ID))
                .thenAnswer(invocation -> MovieDetails.from(movie, Optional.empty()));
        lenient().when(settingsService.current()).thenReturn(KmmSettings.DEFAULTS);
        lenient().when(nfoSync.readTrailerIfUnknown(movie)).thenReturn(true);
        lenient().when(scraper.displayName()).thenReturn("TMDB");
        lenient().when(scraper.missingConfiguration()).thenReturn(Optional.empty());
        lenient().when(scraperRegistry.trailerScrapersFor(MediaType.MOVIE)).thenReturn(List.of(scraper));
    }

    @Test
    void listsCandidatesInThePreferredLanguageFirst() {
        givenTmdbId();
        when(scraper.findTrailers(any())).thenReturn(List.of(
                candidate("Teaser en", "en", "Teaser", true, 1080, "2020-01-01"),
                candidate("Trailer fr", "fr", "Trailer", true, 2160, "2020-01-01"),
                candidate("Trailer en", "en", "Trailer", true, 720, "2020-01-01"),
                candidate("Trailer de inoffiziell", "de", "Trailer", false, 1080, "2021-01-01"),
                candidate("Trailer de", "de", "Trailer", true, 720, null)));

        MovieTrailerService.TrailerOptions options = service.candidates(MOVIE_ID);

        assertThat(options.candidates()).extracting(TrailerOption::name).containsExactly("Trailer de",
                "Trailer de inoffiziell", "Trailer en", "Teaser en", "Trailer fr");
        assertThat(options.candidates().getFirst().scraperName()).isEqualTo("TMDB");
        assertThat(options.warnings()).isEmpty();
        ArgumentCaptor<ScrapeRequest> request = ArgumentCaptor.forClass(ScrapeRequest.class);
        verify(scraper).findTrailers(request.capture());
        assertThat(request.getValue().uniqueIds()).isEqualTo(Map.of("tmdb", "949", "imdb", "tt0113277"));
    }

    @Test
    void prefersHigherAndNewerTrailersWhenEverythingElseIsEqual() {
        TrailerOption older = TrailerOption.of(candidate("älter", "de", "Trailer", true, 1080, "2019-01-01"), "TMDB");
        TrailerOption newer = TrailerOption.of(candidate("neuer", "de", "Trailer", true, 1080, "2020-01-01"), "TMDB");
        TrailerOption smaller = TrailerOption.of(candidate("klein", "DE", "Trailer", true, null, null), "TMDB");

        List<TrailerOption> sorted = List.of(smaller, older, newer).stream()
                .sorted(MovieTrailerService.preference("de")).toList();

        assertThat(sorted).containsExactly(newer, older, smaller);
    }

    @Test
    void reportsUnconfiguredAndFailingScrapersAsWarnings() {
        givenTmdbId();
        TrailerScraper unconfigured = org.mockito.Mockito.mock(TrailerScraper.class);
        when(unconfigured.missingConfiguration()).thenReturn(Optional.of("YouTube: API-Key fehlt"));
        when(scraperRegistry.trailerScrapersFor(MediaType.MOVIE)).thenReturn(List.of(unconfigured, scraper));
        when(scraper.findTrailers(any())).thenThrow(new ScraperException("nicht erreichbar"));

        MovieTrailerService.TrailerOptions options = service.candidates(MOVIE_ID);

        assertThat(options.candidates()).isEmpty();
        assertThat(options.warnings()).containsExactly("YouTube: API-Key fehlt", "TMDB: nicht erreichbar");
        verify(unconfigured, never()).findTrailers(any());
    }

    @Test
    void needsAProviderIdToSearchForTrailers() {
        assertThatThrownBy(() -> service.candidates(MOVIE_ID))
                .isInstanceOfSatisfying(ScraperProblemException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
        verifyNoInteractions(scraper);
    }

    @Test
    void appliesAPlayableLinkToDatabaseAndNfo() {
        MovieDetails details = service.apply(MOVIE_ID, "  " + GERMAN_TRAILER + " ");

        assertThat(details.trailer()).isEqualTo(GERMAN_TRAILER);
        assertThat(movie.nfoTrailer()).isEqualTo(GERMAN_TRAILER);
        verify(nfoSync).refreshIfChangedOutside(movie);
        verify(supplementWriter).write(any(Movie.class), any(MovieSupplement.class));
    }

    @Test
    void rejectsLinksKodiCannotPlay() {
        for (String invalid : new String[] {"YouTube", "ftp://server/trailer.mp4", " ", null}) {
            assertThatThrownBy(() -> service.apply(MOVIE_ID, invalid))
                    .isInstanceOfSatisfying(ScraperProblemException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verifyNoInteractions(supplementWriter);
    }

    @Test
    void removesTheTrailerFromDatabaseAndNfo() {
        movie.changeTrailer(GERMAN_TRAILER);

        MovieDetails details = service.remove(MOVIE_ID);

        assertThat(details.trailer()).isNull();
        assertThat(movie.nfoTrailer()).isEqualTo(MovieNfoSupplement.NO_TRAILER);
        verify(supplementWriter).write(any(Movie.class), any(MovieSupplement.class));
    }

    @Test
    void fillsAMissingTrailerWithTheBestCandidate() {
        givenTmdbId();
        when(scraper.findTrailers(any())).thenReturn(List.of(
                candidate("Trailer en", "en", "Trailer", true, 1080, null),
                candidate("Trailer de", "de", "Trailer", true, 1080, null)));

        assertThat(service.fillMissing(MOVIE_ID)).isTrue();

        assertThat(movie.getTrailer()).isEqualTo(GERMAN_TRAILER);
        verify(supplementWriter).write(any(Movie.class), any(MovieSupplement.class));
    }

    @Test
    void keepsAnExistingTrailerWhenFilling() {
        givenTmdbId();
        movie.changeTrailer("https://example.org/trailer.mp4");

        assertThat(service.fillMissing(MOVIE_ID)).isFalse();

        verify(nfoSync).refreshIfChangedOutside(movie);
        verifyNoInteractions(scraper, supplementWriter);
    }

    @Test
    void leavesTheNfoAloneWhenItsTrailerCannotBeRead() {
        givenTmdbId();
        when(nfoSync.readTrailerIfUnknown(movie)).thenReturn(false);

        assertThat(service.fillMissing(MOVIE_ID)).isFalse();

        verifyNoInteractions(scraper, supplementWriter);
    }

    @Test
    void skipsMoviesWithoutProviderIdOrCandidatesWhenFilling() {
        assertThat(service.fillMissing(MOVIE_ID)).isFalse();
        verifyNoInteractions(scraper);

        givenTmdbId();
        when(scraper.findTrailers(any())).thenReturn(List.of());

        assertThat(service.fillMissing(MOVIE_ID)).isFalse();
        verifyNoInteractions(supplementWriter);
    }

    private void givenTmdbId() {
        movie.applyMetadata(new MovieMetadata("Heat", null, null, 1995, null, null, null, null, null, null,
                List.of(), "tt0113277", "949", null, null));
    }

    private static TrailerCandidate candidate(String name, String language, String type, boolean official,
            Integer height, String published) {
        String id = name.equals("Trailer de") ? "de1" : name.replace(' ', '-');
        return new TrailerCandidate(name, "plugin://plugin.video.youtube/?action=play_video&videoid=" + id,
                URI.create("https://www.youtube.com/watch?v=" + id), null, language, type, official, height,
                published);
    }
}
