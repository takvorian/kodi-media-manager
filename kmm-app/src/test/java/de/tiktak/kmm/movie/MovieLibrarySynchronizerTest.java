package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tiktak.kmm.nfo.MovieNfo;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.scanner.DiscFormat;
import de.tiktak.kmm.scanner.MovieCandidate;
import de.tiktak.kmm.source.MediaSourceService;

@ExtendWith(MockitoExtension.class)
class MovieLibrarySynchronizerTest {

    private static final long SOURCE_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");
    private static final Instant NFO_TIME = Instant.parse("2026-10-20T08:00:00Z");
    private static final Path ROOT = Path.of("library").toAbsolutePath();

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private MediaSourceService sourceService;

    @Mock
    private MovieNfoFiles nfoFiles;

    @Captor
    private ArgumentCaptor<List<Movie>> moviesCaptor;

    private MovieLibrarySynchronizer synchronizer;

    @BeforeEach
    void setUp() {
        synchronizer = new MovieLibrarySynchronizer(movieRepository, sourceService, nfoFiles,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void addsNewMoviesRemovesVanishedAndKeepsKnownOnes() {
        Movie heat = movie("Heat.mkv", "Heat (bearbeitet)");
        Movie vanished = movie("Ronin.mkv", "Ronin");
        when(movieRepository.findBySourceId(SOURCE_ID)).thenReturn(List.of(heat, vanished));

        ScanResult result = synchronizer.synchronize(SOURCE_ID, List.of(
                scanned("Heat.mkv", "Heat", 1995),
                scanned("The Matrix.mkv", "The Matrix", 1999)));

        assertThat(result).isEqualTo(new ScanResult(SOURCE_ID, 1, 0, 1, 1, 0));
        verify(movieRepository).saveAll(moviesCaptor.capture());
        assertThat(moviesCaptor.getValue()).singleElement().satisfies(movie -> {
            assertThat(movie.getTitle()).isEqualTo("The Matrix");
            assertThat(movie.getReleaseYear()).isEqualTo(1999);
            assertThat(movie.getSourceId()).isEqualTo(SOURCE_ID);
            assertThat(movie.getFilePath()).isEqualTo(ROOT.resolve("The Matrix.mkv").toString());
            assertThat(movie.getNfoModifiedAt()).isNull();
        });
        verify(movieRepository).deleteAll(moviesCaptor.capture());
        assertThat(moviesCaptor.getValue()).containsExactly(vanished);
        assertThat(heat.getTitle()).isEqualTo("Heat (bearbeitet)");
    }

    @Test
    void takesMetadataOfNewMovieFromNfo() {
        when(movieRepository.findBySourceId(SOURCE_ID)).thenReturn(List.of());
        ScannedMovie scanned = scannedWithNfo("Heat.mkv", "Heat", null);
        when(nfoFiles.read(scanned.nfo().orElseThrow())).thenReturn(nfo("Heat – Die Saga", null, "tt0113277"));

        synchronizer.synchronize(SOURCE_ID, List.of(scanned));

        verify(movieRepository).saveAll(moviesCaptor.capture());
        Movie movie = moviesCaptor.getValue().getFirst();
        assertThat(movie.getTitle()).isEqualTo("Heat – Die Saga");
        assertThat(movie.getImdbId()).isEqualTo("tt0113277");
        assertThat(movie.getNfoModifiedAt()).isEqualTo(NFO_TIME);
    }

    @Test
    void keepsTitleAndYearFromFileNameWhenNfoLacksThem() {
        when(movieRepository.findBySourceId(SOURCE_ID)).thenReturn(List.of());
        ScannedMovie scanned = scannedWithNfo("Heat.mkv", "Heat", 1995);
        when(nfoFiles.read(scanned.nfo().orElseThrow())).thenReturn(nfo(null, null, "tt0113277"));

        synchronizer.synchronize(SOURCE_ID, List.of(scanned));

        verify(movieRepository).saveAll(moviesCaptor.capture());
        assertThat(moviesCaptor.getValue().getFirst().metadata())
                .extracting(MovieMetadata::title, MovieMetadata::year)
                .containsExactly("Heat", 1995);
    }

    @Test
    void rereadsNfoOfKnownMovieOnlyWhenItChanged() {
        Movie changed = movie("Heat.mkv", "Heat");
        changed.markNfoSynchronized(NFO_TIME.minusSeconds(60));
        Movie untouched = movie("Ronin.mkv", "Ronin");
        untouched.markNfoSynchronized(NFO_TIME);
        when(movieRepository.findBySourceId(SOURCE_ID)).thenReturn(List.of(changed, untouched));
        ScannedMovie heat = scannedWithNfo("Heat.mkv", "Heat", null);
        when(nfoFiles.read(heat.nfo().orElseThrow())).thenReturn(nfo("Heat (neu)", 1995, null));

        ScanResult result = synchronizer.synchronize(SOURCE_ID,
                List.of(heat, scannedWithNfo("Ronin.mkv", "Ronin", null)));

        assertThat(result).isEqualTo(new ScanResult(SOURCE_ID, 0, 1, 0, 1, 0));
        assertThat(changed.getTitle()).isEqualTo("Heat (neu)");
        assertThat(changed.getNfoModifiedAt()).isEqualTo(NFO_TIME);
        assertThat(untouched.getTitle()).isEqualTo("Ronin");
    }

    @Test
    void keepsMetadataWhenNfoDisappears() {
        Movie heat = movie("Heat.mkv", "Heat (gepflegt)");
        heat.markNfoSynchronized(NFO_TIME);
        when(movieRepository.findBySourceId(SOURCE_ID)).thenReturn(List.of(heat));

        ScanResult result = synchronizer.synchronize(SOURCE_ID, List.of(scanned("Heat.mkv", "Heat", null)));

        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(heat.getTitle()).isEqualTo("Heat (gepflegt)");
        verify(nfoFiles, never()).read(any());
    }

    @Test
    void countsUnreadableNfoButKeepsMovie() {
        when(movieRepository.findBySourceId(SOURCE_ID)).thenReturn(List.of());
        ScannedMovie scanned = scannedWithNfo("Heat.mkv", "Heat", 1995);
        when(nfoFiles.read(scanned.nfo().orElseThrow())).thenThrow(unreadableNfo());

        ScanResult result = synchronizer.synchronize(SOURCE_ID, List.of(scanned));

        assertThat(result).isEqualTo(new ScanResult(SOURCE_ID, 1, 0, 0, 0, 1));
        verify(movieRepository).saveAll(moviesCaptor.capture());
        assertThat(moviesCaptor.getValue().getFirst().getTitle()).isEqualTo("Heat");
    }

    @Test
    void countsUnreadableChangedNfoOfKnownMovieAsUnchanged() {
        Movie heat = movie("Heat.mkv", "Heat");
        when(movieRepository.findBySourceId(SOURCE_ID)).thenReturn(List.of(heat));
        ScannedMovie scanned = scannedWithNfo("Heat.mkv", "Heat", null);
        when(nfoFiles.read(scanned.nfo().orElseThrow())).thenThrow(unreadableNfo());

        ScanResult result = synchronizer.synchronize(SOURCE_ID, List.of(scanned));

        assertThat(result).isEqualTo(new ScanResult(SOURCE_ID, 0, 0, 0, 1, 1));
    }

    @Test
    void marksSourceAsScannedWithCurrentTime() {
        when(movieRepository.findBySourceId(SOURCE_ID)).thenReturn(List.of());

        synchronizer.synchronize(SOURCE_ID, List.of());

        verify(sourceService).markScanned(SOURCE_ID, NOW);
    }

    private static Movie movie(String fileName, String title) {
        return new Movie(SOURCE_ID, ROOT.resolve(fileName).toString(), title, null, DiscFormat.FILE, 1);
    }

    private static ScannedMovie scanned(String fileName, String title, Integer year) {
        return new ScannedMovie(candidate(fileName, title, year), Optional.empty());
    }

    private static ScannedMovie scannedWithNfo(String fileName, String title, Integer year) {
        Path nfo = ROOT.resolve(fileName.replace(".mkv", ".nfo"));
        return new ScannedMovie(candidate(fileName, title, year), Optional.of(new NfoFileState(nfo, NFO_TIME)));
    }

    private static MovieCandidate candidate(String fileName, String title, Integer year) {
        return new MovieCandidate(ROOT.resolve(fileName), title, year, DiscFormat.FILE, 1);
    }

    private static MovieNfo nfo(String title, Integer year, String imdbId) {
        Map<String, String> ids = imdbId == null ? Map.of() : Map.of(MovieNfo.IMDB, imdbId);
        return new MovieNfo(title, null, null, year, null, null, null, null, null, null, List.of("Krimi"), ids);
    }

    private static NfoException unreadableNfo() {
        return mock(NfoException.class);
    }
}
