package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tiktak.kmm.nfo.MovieNfo;
import de.tiktak.kmm.nfo.MovieNfoSupplement;
import de.tiktak.kmm.nfo.NfoActor;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.scanner.DiscFormat;

@ExtendWith(MockitoExtension.class)
class MovieNfoSyncTest {

    private static final Path MOVIE_FILE = Path.of("library", "Heat.mkv").toAbsolutePath();
    private static final Path NFO_FILE = MOVIE_FILE.resolveSibling("Heat.nfo");
    private static final Instant SYNCHRONIZED = Instant.parse("2026-10-27T12:00:00Z");
    private static final Instant CHANGED = Instant.parse("2026-10-28T12:00:00Z");

    @Mock
    private MovieNfoFiles nfoFiles;

    @Mock
    private MovieSets movieSets;

    private MovieNfoSync nfoSync;
    private Movie movie;

    @BeforeEach
    void setUp() {
        nfoSync = new MovieNfoSync(nfoFiles, movieSets, Clock.fixed(CHANGED, ZoneOffset.UTC));
        movie = new Movie(1L, MOVIE_FILE.toString(), "Heat", 1995, DiscFormat.FILE, 1);
        movie.markNfoSynchronized(SYNCHRONIZED);
        movie.markNfoRead();
    }

    @Test
    void readsTheTrailerOfOlderMoviesFromTheNfo() {
        NfoFileState unchanged = new NfoFileState(NFO_FILE, SYNCHRONIZED);
        when(nfoFiles.find(movie.companionFiles())).thenReturn(Optional.of(unchanged));
        when(nfoFiles.read(unchanged)).thenReturn(nfoWithTrailer("plugin://plugin.video.youtube/?videoid=abc"));

        assertThat(nfoSync.readTrailerIfUnknown(movie)).isTrue();
        assertThat(nfoSync.readTrailerIfUnknown(movie)).as("nur einmal lesen").isTrue();

        assertThat(movie.getTrailer()).isEqualTo("plugin://plugin.video.youtube/?videoid=abc");
        assertThat(movie.getTitle()).as("nur der Trailer").isEqualTo("Heat");
        verify(nfoFiles).read(unchanged);
    }

    @Test
    void knowsThereIsNoTrailerWithoutNfoOrTrailerElement() {
        Movie other = new Movie(1L, MOVIE_FILE.toString(), "Heat", 1995, DiscFormat.FILE, 1);
        NfoFileState unchanged = new NfoFileState(NFO_FILE, SYNCHRONIZED);
        when(nfoFiles.find(movie.companionFiles())).thenReturn(Optional.empty()).thenReturn(Optional.of(unchanged));
        when(nfoFiles.read(unchanged)).thenReturn(nfoWithTrailer(MovieNfoSupplement.NO_TRAILER));

        assertThat(nfoSync.readTrailerIfUnknown(movie)).isTrue();
        assertThat(nfoSync.readTrailerIfUnknown(other)).isTrue();

        assertThat(movie.isTrailerKnown()).isTrue();
        assertThat(other.getTrailer()).isNull();
        assertThat(other.isTrailerKnown()).isTrue();
    }

    @Test
    void keepsTheTrailerUnknownWhenTheNfoIsUnreadable() {
        NfoFileState unchanged = new NfoFileState(NFO_FILE, SYNCHRONIZED);
        when(nfoFiles.find(movie.companionFiles())).thenReturn(Optional.of(unchanged));
        when(nfoFiles.read(unchanged)).thenThrow(mock(NfoException.class));

        assertThat(nfoSync.readTrailerIfUnknown(movie)).isFalse();
        assertThat(movie.isTrailerKnown()).isFalse();
    }

    @Test
    void readsANfoThatChangedSinceItWasLastRead() {
        NfoFileState changed = new NfoFileState(NFO_FILE, CHANGED);
        when(nfoFiles.find(movie.companionFiles())).thenReturn(Optional.of(changed));
        when(nfoFiles.read(changed)).thenReturn(new MovieNfo("Heat (Director's Cut)", null, null, 1995, null, null,
                null, null, null, null, null, null, List.of("Krimi"), Map.of()));

        nfoSync.refreshIfChangedOutside(movie);

        assertThat(movie.getTitle()).isEqualTo("Heat (Director's Cut)");
        assertThat(movie.getNfoModifiedAt()).isEqualTo(CHANGED);
    }

    @Test
    void leavesTheMovieAloneIfTheNfoIsUnchangedOrMissing() {
        when(nfoFiles.find(movie.companionFiles()))
                .thenReturn(Optional.of(new NfoFileState(NFO_FILE, SYNCHRONIZED)))
                .thenReturn(Optional.empty());

        nfoSync.refreshIfChangedOutside(movie);
        nfoSync.refreshIfChangedOutside(movie);

        verify(nfoFiles, never()).read(any());
        assertThat(movie.getTitle()).isEqualTo("Heat");
    }

    @Test
    void refusesToContinueIfTheChangedNfoIsUnreadable() {
        NfoFileState changed = new NfoFileState(NFO_FILE, CHANGED);
        when(nfoFiles.find(movie.companionFiles())).thenReturn(Optional.of(changed));
        when(nfoFiles.read(changed)).thenThrow(mock(NfoException.class));

        assertThatThrownBy(() -> nfoSync.refreshIfChangedOutside(movie))
                .isInstanceOfSatisfying(NfoChangedOutsideException.class,
                        e -> assertThat(e.getBody().getDetail()).contains(NFO_FILE.toString()));
        assertThat(movie.getNfoModifiedAt()).isEqualTo(SYNCHRONIZED);
    }

    @Test
    void readsTheNfoOfAnEmberMovieFirstAndKeepsWhatOnlyEmberKnows() {
        StreamDetails ember = new StreamDetails(List.of(), List.of(new StreamDetails.Audio("ac3", "deu", 6, null)),
                List.of());
        movie.applySupplement(MovieSupplement.EMPTY.withActors(List.of(new MovieSupplement.Actor("Al Pacino",
                "Hanna", null), new MovieSupplement.Actor("Val Kilmer", "Chris", null))), null);
        movie.applyStreamDetails(ember);
        movie.changeTrailer("plugin://plugin.video.youtube/?videoid=ember");
        movie.markNfoNotRead();
        NfoFileState unchanged = new NfoFileState(NFO_FILE, SYNCHRONIZED);
        when(nfoFiles.find(movie.companionFiles())).thenReturn(Optional.of(unchanged));
        MovieNfoSupplement fromNfo = MovieNfoSupplement.EMPTY.withTrailer(MovieNfoSupplement.NO_TRAILER);
        when(nfoFiles.read(unchanged)).thenReturn(new MovieNfo("Heat (NFO)", null, null, 1995, null, null, null, null,
                null, null, null, null, List.of(), Map.of(), new MovieNfoSupplement(List.of(new NfoActor("Al Pacino",
                "Lt. Vincent Hanna", 0, null)), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null,
                9, null, null, null, StreamDetails.EMPTY, MovieNfoSupplement.NO_TRAILER)));

        nfoSync.refreshIfChangedOutside(movie);
        nfoSync.refreshIfChangedOutside(movie);

        assertThat(movie.getTitle()).isEqualTo("Heat (NFO)");
        assertThat(movie.supplement().userRating()).isEqualTo(9);
        assertThat(movie.supplement().actors()).extracting(MovieSupplement.Actor::role)
                .containsExactly("Lt. Vincent Hanna", "Chris");
        assertThat(movie.streamDetails()).isEqualTo(ember);
        assertThat(movie.getTrailer()).isEqualTo("plugin://plugin.video.youtube/?videoid=ember");
        assertThat(movie.isNfoRead()).isTrue();
        verify(nfoFiles).read(unchanged);
        assertThat(fromNfo.trailer()).isEqualTo(MovieNfoSupplement.NO_TRAILER);
    }

    private static MovieNfo nfoWithTrailer(String trailer) {
        return new MovieNfo("Heat (Director's Cut)", null, null, 1995, null, null, null, null, null, null, null, null,
                List.of(), Map.of(), MovieNfoSupplement.EMPTY.withTrailer(trailer));
    }
}
