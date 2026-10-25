package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
import de.tiktak.kmm.nfo.NfoActor;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.scanner.DiscFormat;

@ExtendWith(MockitoExtension.class)
class MovieMetadataServiceTest {

    private static final long MOVIE_ID = 5L;
    private static final Path MOVIE_FILE = Path.of("library", "Heat.mkv").toAbsolutePath();
    private static final Path NFO_FILE = MOVIE_FILE.resolveSibling("Heat.nfo");
    private static final Instant NFO_TIME = Instant.parse("2026-10-25T12:00:00Z");

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private MovieNfoFiles nfoFiles;

    private MovieMetadataService service;

    @BeforeEach
    void setUp() {
        service = new MovieMetadataService(movieRepository, nfoFiles, Clock.fixed(NFO_TIME, ZoneOffset.UTC));
    }

    @Test
    void returnsDetailsWithExistingNfo() {
        Movie movie = givenMovie();
        when(nfoFiles.find(movie.companionFiles())).thenReturn(Optional.of(new NfoFileState(NFO_FILE, NFO_TIME)));

        MovieDetails details = service.findDetails(MOVIE_ID);

        assertThat(details.id()).isEqualTo(MOVIE_ID);
        assertThat(details.metadata().title()).isEqualTo("Heat");
        assertThat(details.nfo()).isEqualTo(new MovieDetails.NfoInfo(NFO_FILE.toString(), true, NFO_TIME));
    }

    @Test
    void returnsPlannedNfoPathWhenNfoIsMissing() {
        Movie movie = givenMovie();
        when(nfoFiles.find(movie.companionFiles())).thenReturn(Optional.empty());

        MovieDetails details = service.findDetails(MOVIE_ID);

        assertThat(details.nfo()).isEqualTo(new MovieDetails.NfoInfo(NFO_FILE.toString(), false, null));
    }

    @Test
    void writesNfoAndStoresMetadata() {
        Movie movie = givenMovie();
        MovieMetadata metadata = metadata("Heat (Director's Cut)");
        when(nfoFiles.write(eq(movie.companionFiles()), eq(metadata), isNull()))
                .thenReturn(new NfoFileState(NFO_FILE, NFO_TIME));

        MovieDetails details = service.update(MOVIE_ID, metadata);

        assertThat(movie.getTitle()).isEqualTo("Heat (Director's Cut)");
        assertThat(movie.getGenres()).containsExactly("Krimi", "Drama");
        assertThat(movie.getNfoModifiedAt()).isEqualTo(NFO_TIME);
        assertThat(details.nfo().exists()).isTrue();
    }

    @Test
    void writesKnownSupplementWithDateAddedIntoTheNfo() {
        Movie movie = givenMovie();
        movie.applySupplement(new MovieSupplement(List.of(new MovieSupplement.Actor("Al Pacino", "Hanna", null)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, 9, null, null), null);
        movie.changeAddedAt(Instant.parse("2016-11-30T01:20:15Z"));
        ArgumentCaptor<MovieNfoSupplement> written = ArgumentCaptor.forClass(MovieNfoSupplement.class);
        when(nfoFiles.write(any(), any(), written.capture())).thenReturn(new NfoFileState(NFO_FILE, NFO_TIME));

        MovieDetails details = service.update(MOVIE_ID, metadata("Heat"));

        assertThat(written.getValue().actors()).containsExactly(new NfoActor("Al Pacino", "Hanna", 0, null));
        assertThat(written.getValue().userRating()).isEqualTo(9);
        assertThat(written.getValue().dateAdded()).isEqualTo(LocalDateTime.of(2016, 11, 30, 1, 20, 15));
        assertThat(details.supplement().actors()).extracting(MovieSupplement.Actor::name).containsExactly("Al Pacino");
    }

    @Test
    void keepsDatabaseUnchangedWhenNfoCannotBeWritten() {
        Movie movie = givenMovie();
        when(nfoFiles.write(any(), any(), any())).thenThrow(mock(NfoException.class));

        assertThatThrownBy(() -> service.update(MOVIE_ID, metadata("Neu")))
                .isInstanceOfSatisfying(NfoNotWritableException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(exception.getBody().getDetail()).contains("Schreibrechte");
                });
        assertThat(movie.getTitle()).isEqualTo("Heat");
    }

    @Test
    void rejectsUnknownMovie() {
        when(movieRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findDetails(99L))
                .isInstanceOfSatisfying(MovieNotFoundException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private Movie givenMovie() {
        Movie movie = new Movie(1L, MOVIE_FILE.toString(), "Heat", 1995, DiscFormat.FILE, 1);
        ReflectionTestUtils.setField(movie, "id", MOVIE_ID);
        when(movieRepository.findById(MOVIE_ID)).thenReturn(Optional.of(movie));
        return movie;
    }

    private static MovieMetadata metadata(String title) {
        return new MovieMetadata(title, null, null, 1995, null, null, null, null, null, null,
                List.of("Krimi", "Drama"), "tt0113277", null);
    }
}
