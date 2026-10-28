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
import java.time.LocalDateTime;
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
import de.tiktak.kmm.nfo.MovieNfoSupplement;
import de.tiktak.kmm.nfo.NfoActor;
import de.tiktak.kmm.nfo.NfoMovieSet;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.nfo.StreamDetails;
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

    @Mock
    private MovieSets movieSets;

    @Captor
    private ArgumentCaptor<List<Movie>> moviesCaptor;

    private MovieLibrarySynchronizer synchronizer;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        synchronizer = new MovieLibrarySynchronizer(movieRepository, sourceService, nfoFiles,
                new MovieNfoSync(nfoFiles, movieSets, clock), clock);
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
    void takesSupplementSetAndDateAddedFromNfo() {
        when(movieRepository.findBySourceId(SOURCE_ID)).thenReturn(List.of());
        ScannedMovie scanned = scannedWithNfo("Heat.mkv", "Heat", null);
        MovieSet set = new MovieSet("Mann Collection");
        when(movieSets.resolve(new MovieSupplement.SetReference("Mann Collection", null))).thenReturn(set);
        when(nfoFiles.read(scanned.nfo().orElseThrow())).thenReturn(withSupplement(nfo("Heat", 1995, null),
                new MovieNfoSupplement(List.of(new NfoActor("Al Pacino", "Hanna", 0, null)), List.of("Michael Mann"),
                        List.of(), List.of(), List.of(), List.of(), List.of(), new NfoMovieSet("Mann Collection", null),
                        null, 2, null, LocalDateTime.of(2016, 11, 30, 1, 20, 15),
                        new StreamDetails(List.of(), List.of(new StreamDetails.Audio("ac3", "deu", 6, null)), List.of()))));

        synchronizer.synchronize(SOURCE_ID, List.of(scanned));

        verify(movieRepository).saveAll(moviesCaptor.capture());
        Movie movie = moviesCaptor.getValue().getFirst();
        assertThat(movie.supplement().actors()).extracting(MovieSupplement.Actor::name).containsExactly("Al Pacino");
        assertThat(movie.supplement().directors()).containsExactly("Michael Mann");
        assertThat(movie.streamDetails().audios()).extracting(StreamDetails.Audio::codec).containsExactly("ac3");
        assertThat(movie.getMovieSet()).isSameAs(set);
        assertThat(movie.getPlayCount()).isEqualTo(2);
        assertThat(movie.getAddedAt()).isEqualTo(Instant.parse("2016-11-30T01:20:15Z"));
    }

    @Test
    void treatsSupplementOfNewMovieWithoutNfoAsKnownButNotWithUnreadableNfo() {
        when(movieRepository.findBySourceId(SOURCE_ID)).thenReturn(List.of());
        ScannedMovie broken = scannedWithNfo("Ronin.mkv", "Ronin", null);
        when(nfoFiles.read(broken.nfo().orElseThrow())).thenThrow(unreadableNfo());

        synchronizer.synchronize(SOURCE_ID, List.of(scanned("Heat.mkv", "Heat", null), broken));

        verify(movieRepository).saveAll(moviesCaptor.capture());
        assertThat(moviesCaptor.getValue()).extracting(Movie::isSupplementKnown).containsExactly(true, false);
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

    @Test
    void usesNfoReadDuringTheScanInsteadOfReadingAgain() {
        when(movieRepository.findBySourceId(SOURCE_ID)).thenReturn(List.of());
        ScannedMovie read = scannedWithNfo("Heat.mkv", "Heat", null);
        ScannedMovie preloaded = new ScannedMovie(read.candidate(), read.nfo(),
                Optional.of(LoadedMovieNfo.read(() -> nfo("Heat – Die Saga", 1995, null))));
        ScannedMovie broken = scannedWithNfo("Ronin.mkv", "Ronin", null);
        ScannedMovie brokenPreloaded = new ScannedMovie(broken.candidate(), broken.nfo(),
                Optional.of(LoadedMovieNfo.read(() -> {
                    throw unreadableNfo();
                })));

        ScanResult result = synchronizer.synchronize(SOURCE_ID, List.of(preloaded, brokenPreloaded));

        assertThat(result.nfoErrors()).isEqualTo(1);
        verify(movieRepository).saveAll(moviesCaptor.capture());
        assertThat(moviesCaptor.getValue()).extracting(Movie::getTitle).containsExactly("Heat – Die Saga", "Ronin");
        verify(nfoFiles, never()).read(any());
    }

    @Test
    void knowsTheNfoTimesOfMoviesWithReadNfo() {
        when(movieRepository.findNfoTimestampsBySourceId(SOURCE_ID)).thenReturn(List.of(
                new Object[] {"/filme/Heat.mkv", NFO_TIME}, new Object[] {"/filme/Ronin.mkv", null}));

        assertThat(synchronizer.knownNfos(SOURCE_ID)).containsExactly(Map.entry("/filme/Heat.mkv", NFO_TIME));
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
        return new MovieNfo(title, null, null, year, null, null, null, null, null, null, null, null, List.of("Krimi"), ids);
    }

    private static MovieNfo withSupplement(MovieNfo nfo, MovieNfoSupplement supplement) {
        return new MovieNfo(nfo.title(), nfo.originalTitle(), nfo.sortTitle(), nfo.year(), nfo.premiered(), nfo.plot(),
                nfo.outline(), nfo.tagline(), nfo.runtime(), nfo.mpaa(), null, null, nfo.genres(), nfo.uniqueIds(), supplement);
    }

    private static NfoException unreadableNfo() {
        return mock(NfoException.class);
    }
}
