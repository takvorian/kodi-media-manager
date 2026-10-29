package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.scanner.DiscFormat;
import de.tiktak.kmm.settings.KmmSettings;
import de.tiktak.kmm.settings.SettingsService;
import de.tiktak.kmm.trailer.TrailerFinder;
import de.tiktak.kmm.trailer.TrailerOption;
import de.tiktak.kmm.trailer.TrailerOptions;

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
    private TrailerFinder trailerFinder;

    @Mock
    private SettingsService settingsService;

    private MovieTrailerService service;
    private Movie movie;

    @BeforeEach
    void setUp() {
        service = new MovieTrailerService(movieRepository, metadataService, nfoSync, supplementWriter, trailerFinder,
                settingsService);
        movie = new Movie(1L, "/filme/Heat.mkv", "Heat", 1995, DiscFormat.FILE, 1);
        ReflectionTestUtils.setField(movie, "id", MOVIE_ID);
        lenient().when(movieRepository.findById(MOVIE_ID)).thenReturn(Optional.of(movie));
        lenient().when(metadataService.findDetails(MOVIE_ID))
                .thenAnswer(invocation -> MovieDetails.from(movie, Optional.empty()));
        lenient().when(settingsService.current()).thenReturn(KmmSettings.DEFAULTS);
        lenient().when(nfoSync.readTrailerIfUnknown(movie)).thenReturn(true);
    }

    @Test
    void searchesTrailersByProviderIdsInThePreferredArtworkLanguage() {
        givenTmdbId();
        TrailerOptions options = options(GERMAN_TRAILER);
        when(trailerFinder.find(any(), eq(KmmSettings.DEFAULTS.artworkLanguage()))).thenReturn(options);

        assertThat(service.candidates(MOVIE_ID)).isEqualTo(options);

        ArgumentCaptor<ScrapeRequest> request = ArgumentCaptor.forClass(ScrapeRequest.class);
        verify(trailerFinder).find(request.capture(), any());
        assertThat(request.getValue().mediaType()).isEqualTo(MediaType.MOVIE);
        assertThat(request.getValue().uniqueIds()).isEqualTo(Map.of("tmdb", "949", "imdb", "tt0113277"));
    }

    @Test
    void needsAProviderIdToSearchForTrailers() {
        assertThatThrownBy(() -> service.candidates(MOVIE_ID))
                .isInstanceOfSatisfying(ScraperProblemException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
        verifyNoInteractions(trailerFinder);
    }

    @Test
    void appliesAPlayableLinkToDatabaseAndNfo() {
        MovieDetails details = service.apply(MOVIE_ID, "  " + GERMAN_TRAILER + " ");

        assertThat(details.trailer()).isEqualTo(GERMAN_TRAILER);
        assertThat(movie.nfoTrailer()).isEqualTo(GERMAN_TRAILER);
        verify(nfoSync).refreshIfChangedOutside(movie);
        verify(supplementWriter).writeTrailer(movie);
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
        verify(supplementWriter).writeTrailer(movie);
    }

    @Test
    void fillsAMissingTrailerWithTheBestCandidate() {
        givenTmdbId();
        when(trailerFinder.find(any(), any())).thenReturn(options(GERMAN_TRAILER, "https://example.org/en.mp4"));

        assertThat(service.fillMissing(MOVIE_ID)).isTrue();

        assertThat(movie.getTrailer()).isEqualTo(GERMAN_TRAILER);
        verify(supplementWriter).writeTrailer(movie);
    }

    @Test
    void keepsAnExistingTrailerWhenFilling() {
        givenTmdbId();
        movie.changeTrailer("https://example.org/trailer.mp4");

        assertThat(service.fillMissing(MOVIE_ID)).isFalse();

        verify(nfoSync).refreshIfChangedOutside(movie);
        verifyNoInteractions(trailerFinder, supplementWriter);
    }

    @Test
    void leavesTheNfoAloneWhenItsTrailerCannotBeRead() {
        givenTmdbId();
        when(nfoSync.readTrailerIfUnknown(movie)).thenReturn(false);

        assertThat(service.fillMissing(MOVIE_ID)).isFalse();

        verifyNoInteractions(trailerFinder, supplementWriter);
    }

    @Test
    void skipsMoviesWithoutProviderIdOrCandidatesWhenFilling() {
        assertThat(service.fillMissing(MOVIE_ID)).isFalse();
        verifyNoInteractions(trailerFinder);

        givenTmdbId();
        when(trailerFinder.find(any(), any())).thenReturn(options());

        assertThat(service.fillMissing(MOVIE_ID)).isFalse();
        verifyNoInteractions(supplementWriter);
    }

    private void givenTmdbId() {
        movie.applyMetadata(new MovieMetadata("Heat", null, null, 1995, null, null, null, null, null, null,
                List.of(), "tt0113277", "949", null, null));
    }

    private static TrailerOptions options(String... urls) {
        return new TrailerOptions(List.of(urls).stream()
                .map(url -> new TrailerOption("Trailer", url, URI.create("https://www.youtube.com/watch?v=de1"), null,
                        "de", "Trailer", true, 1080, null, "TMDB"))
                .toList(), List.of());
    }
}
