package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import de.tiktak.kmm.source.MediaSourceService;
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

    @Mock
    private ApplicationEventPublisher events;

    @Mock
    private MovieNfoSync nfoSync;

    @Mock
    private MediaSourceService sourceService;

    private MovieMetadataService service;

    @BeforeEach
    void setUp() {
        service = new MovieMetadataService(movieRepository, nfoFiles, nfoSync, Clock.fixed(NFO_TIME, ZoneOffset.UTC),
                events, sourceService);
    }

    @Test
    void prefersTheLanguageOfTheMovieOverThatOfItsSource() {
        Movie movie = new Movie(3L, "/filme/Heat.mkv", "Heat", 1995, DiscFormat.FILE, 1);
        when(movieRepository.findById(5L)).thenReturn(Optional.of(movie));
        when(sourceService.languageOf(3L)).thenReturn("en-US");

        assertThat(service.scrapeLanguage(5L)).isEqualTo("en-US");

        movie.changeMetadataLanguage("fr-FR");
        assertThat(service.scrapeLanguage(5L)).isEqualTo("fr-FR");
    }

    @Test
    void writesAChangedLanguageIntoAnExistingNfo() {
        Movie movie = new Movie(3L, "/filme/Heat.mkv", "Heat", 1995, DiscFormat.FILE, 1);
        NfoFileState nfo = new NfoFileState(Path.of("/filme/Heat.nfo"), NFO_TIME);
        Instant written = NFO_TIME.plusSeconds(60);
        when(movieRepository.findById(5L)).thenReturn(Optional.of(movie));
        when(nfoFiles.find(any())).thenReturn(Optional.of(nfo));
        when(nfoFiles.writeLanguage(nfo, "fr-FR")).thenReturn(new NfoFileState(nfo.file(), written));

        assertThat(service.changeLanguage(5L, "fr-FR")).isTrue();

        assertThat(movie.getMetadataLanguage()).isEqualTo("fr-FR");
        assertThat(movie.getNfoModifiedAt()).isEqualTo(written);
        assertThat(service.changeLanguage(5L, "fr-FR")).as("unverändert").isFalse();
        verify(nfoSync, times(2)).refreshIfChangedOutside(movie);
        verify(nfoFiles).writeLanguage(nfo, "fr-FR");
    }

    @Test
    void changesTheLanguageOfAMovieWithoutNfo() {
        Movie movie = new Movie(3L, "/filme/Heat.mkv", "Heat", 1995, DiscFormat.FILE, 1);
        when(movieRepository.findById(5L)).thenReturn(Optional.of(movie));
        when(nfoFiles.find(any())).thenReturn(Optional.empty());

        assertThat(service.changeLanguage(5L, "fr-FR")).isTrue();

        verify(nfoFiles, never()).writeLanguage(any(), any());
    }

    @Test
    void reportsANfoItCannotWriteTheLanguageInto() {
        Movie movie = new Movie(3L, "/filme/Heat.mkv", "Heat", 1995, DiscFormat.FILE, 1);
        NfoFileState nfo = new NfoFileState(Path.of("/filme/Heat.nfo"), NFO_TIME);
        when(movieRepository.findById(5L)).thenReturn(Optional.of(movie));
        when(nfoFiles.find(any())).thenReturn(Optional.of(nfo));
        when(nfoFiles.writeLanguage(nfo, "fr-FR")).thenThrow(mock(NfoException.class));

        assertThatThrownBy(() -> service.changeLanguage(5L, "fr-FR")).isInstanceOf(NfoNotWritableException.class);
    }

    @Test
    void storesTheLanguageInANewNfoOnlyIfTheMovieHasItsOwn() {
        Movie movie = new Movie(3L, "/filme/Heat.mkv", "Heat", 1995, DiscFormat.FILE, 1);
        NfoFileState nfo = new NfoFileState(Path.of("/filme/Heat.nfo"), NFO_TIME);
        when(movieRepository.findById(5L)).thenReturn(Optional.of(movie));

        service.storeLanguageInNfo(5L);
        verify(nfoFiles, never()).writeLanguage(any(), any());

        movie.changeMetadataLanguage("fr-FR");
        when(nfoFiles.find(any())).thenReturn(Optional.of(nfo));
        when(nfoFiles.writeLanguage(nfo, "fr-FR")).thenReturn(nfo);
        service.storeLanguageInNfo(5L);
        verify(nfoFiles).writeLanguage(nfo, "fr-FR");
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
        verify(events).publishEvent(new MovieFilesChangedEvent(MOVIE_ID));
        InOrder order = inOrder(nfoSync, nfoFiles);
        order.verify(nfoSync).refreshIfChangedOutside(movie);
        order.verify(nfoFiles).write(any(), any(), any());
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
        verifyNoInteractions(events);
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
                List.of("Krimi", "Drama"), "tt0113277", null, null, null);
    }
}
