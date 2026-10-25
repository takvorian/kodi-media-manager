package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tiktak.kmm.scanner.DiscFormat;
import de.tiktak.kmm.scanner.MovieCandidate;
import de.tiktak.kmm.source.MediaSourceService;

@ExtendWith(MockitoExtension.class)
class MovieLibrarySynchronizerTest {

    private static final long SOURCE_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");
    private static final Path ROOT = Path.of("library").toAbsolutePath();

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private MediaSourceService sourceService;

    @Captor
    private ArgumentCaptor<List<Movie>> moviesCaptor;

    private MovieLibrarySynchronizer synchronizer;

    @BeforeEach
    void setUp() {
        synchronizer = new MovieLibrarySynchronizer(movieRepository, sourceService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void addsNewMoviesRemovesVanishedAndKeepsKnownOnes() {
        Movie heat = movie("Heat.mkv", "Heat (bearbeitet)");
        Movie vanished = movie("Ronin.mkv", "Ronin");
        when(movieRepository.findBySourceId(SOURCE_ID)).thenReturn(List.of(heat, vanished));

        ScanResult result = synchronizer.synchronize(SOURCE_ID, List.of(
                candidate("Heat.mkv", "Heat", 1995),
                candidate("The Matrix.mkv", "The Matrix", 1999)));

        assertThat(result).isEqualTo(new ScanResult(SOURCE_ID, 1, 1, 1));
        verify(movieRepository).saveAll(moviesCaptor.capture());
        assertThat(moviesCaptor.getValue()).singleElement().satisfies(movie -> {
            assertThat(movie.getTitle()).isEqualTo("The Matrix");
            assertThat(movie.getReleaseYear()).isEqualTo(1999);
            assertThat(movie.getSourceId()).isEqualTo(SOURCE_ID);
            assertThat(movie.getFilePath()).isEqualTo(ROOT.resolve("The Matrix.mkv").toString());
        });
        verify(movieRepository).deleteAll(moviesCaptor.capture());
        assertThat(moviesCaptor.getValue()).containsExactly(vanished);
        assertThat(heat.getTitle()).isEqualTo("Heat (bearbeitet)");
    }

    @Test
    void marksSourceAsScannedWithCurrentTime() {
        when(movieRepository.findBySourceId(SOURCE_ID)).thenReturn(List.of());

        synchronizer.synchronize(SOURCE_ID, List.of());

        verify(sourceService).markScanned(SOURCE_ID, NOW);
    }

    @Test
    void reportsEmptyResultForEmptySourceAndEmptyScan() {
        when(movieRepository.findBySourceId(SOURCE_ID)).thenReturn(List.of());

        assertThat(synchronizer.synchronize(SOURCE_ID, List.of())).isEqualTo(new ScanResult(SOURCE_ID, 0, 0, 0));
    }

    private static Movie movie(String fileName, String title) {
        return new Movie(SOURCE_ID, ROOT.resolve(fileName).toString(), title, null, DiscFormat.FILE, 1);
    }

    private static MovieCandidate candidate(String fileName, String title, Integer year) {
        return new MovieCandidate(ROOT.resolve(fileName), title, year, DiscFormat.FILE, 1);
    }
}
