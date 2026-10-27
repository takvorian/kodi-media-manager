package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import de.tiktak.kmm.job.JobCancelledException;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.job.JobStatus;
import de.tiktak.kmm.job.JobTask;
import de.tiktak.kmm.nfo.MovieNfo;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.scanner.CompanionFiles;
import de.tiktak.kmm.scanner.DiscFormat;
import de.tiktak.kmm.scanner.MovieCandidate;
import de.tiktak.kmm.scanner.MovieDirectoryScanner;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;

@ExtendWith(MockitoExtension.class)
class MovieScanServiceTest {

    private static final long SOURCE_ID = 3L;

    @Mock
    private MediaSourceService sourceService;

    @Mock
    private MovieDirectoryScanner scanner;

    @Mock
    private MovieScannerFactory scannerFactory;

    @Mock
    private MovieLibrarySynchronizer synchronizer;

    @Mock
    private MovieNfoFiles nfoFiles;

    @Mock
    private JobService jobService;

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private MovieScanService service;

    @TempDir
    Path directory;

    @BeforeEach
    void useMockedScanner() {
        lenient().when(scannerFactory.create()).thenReturn(scanner);
    }

    @Test
    void startsScanAsJobKeyedBySource() {
        givenSource(MediaType.MOVIE, directory);
        JobSnapshot started = new JobSnapshot(UUID.randomUUID(), "scan:3", "Scan „Filme“", JobStatus.RUNNING,
                0, 0, null, null, Instant.now(), null);
        when(jobService.submit(eq("scan:3"), eq("Scan „Filme“"), any())).thenReturn(started);

        assertThat(service.startScan(SOURCE_ID)).isEqualTo(started);
    }

    @Test
    void startedJobScansTheSource() {
        givenSource(MediaType.MOVIE, directory);
        givenScannerFinds();
        when(synchronizer.synchronize(SOURCE_ID, List.of())).thenReturn(new ScanResult(SOURCE_ID, 0, 0, 0, 0, 0));
        service.startScan(SOURCE_ID);
        ArgumentCaptor<JobTask<?>> task = ArgumentCaptor.forClass(JobTask.class);
        verify(jobService).submit(anyString(), anyString(), task.capture());

        Object result = task.getValue().run(new RecordingContext(false));

        assertThat(result).isEqualTo(new ScanResult(SOURCE_ID, 0, 0, 0, 0, 0));
    }

    @Test
    void scanLooksUpNfosReportsProgressAndSynchronizes() {
        MediaSource source = givenSource(MediaType.MOVIE, directory);
        var heat = candidate("Heat.mkv");
        var ronin = candidate("Ronin.mkv");
        var nfo = new NfoFileState(directory.resolve("Heat.nfo"), Instant.parse("2026-10-20T08:00:00Z"));
        var expected = new ScanResult(SOURCE_ID, 2, 0, 0, 0, 0);
        givenScannerFinds(heat, ronin);
        when(synchronizer.knownNfos(SOURCE_ID)).thenReturn(Map.of(heat.file().toString(), nfo.modifiedAt()));
        when(nfoFiles.find(CompanionFiles.of(heat.file(), DiscFormat.FILE, 1))).thenReturn(Optional.of(nfo));
        when(nfoFiles.find(CompanionFiles.of(ronin.file(), DiscFormat.FILE, 1))).thenReturn(Optional.empty());
        when(synchronizer.synchronize(SOURCE_ID, List.of(
                new ScannedMovie(heat, Optional.of(nfo)), new ScannedMovie(ronin, Optional.empty()))))
                .thenReturn(expected);
        RecordingContext context = new RecordingContext(false);

        assertThat(service.scan(source, directory, context)).isEqualTo(expected);
        assertThat(context.progress).containsExactly(
                "0/0 " + MovieScanService.SEARCHING_MESSAGE,
                "1/1 " + MovieScanService.SEARCHING_MESSAGE,
                "1/2 " + MovieScanService.CHECKING_NFOS_MESSAGE,
                "2/2 " + MovieScanService.CHECKING_NFOS_MESSAGE,
                "2/2 " + MovieScanService.SYNCHRONIZING_MESSAGE);
        verify(events).publishEvent(new MovieSourceScannedEvent(SOURCE_ID, 2, 0));
        verify(nfoFiles, never()).read(any());
    }

    @Test
    void readsNewAndChangedNfosBeforeSynchronizing() {
        MediaSource source = givenSource(MediaType.MOVIE, directory);
        var heat = candidate("Heat.mkv");
        var ronin = candidate("Ronin.mkv");
        var heatNfo = new NfoFileState(directory.resolve("Heat.nfo"), Instant.parse("2026-10-20T08:00:00Z"));
        var roninNfo = new NfoFileState(directory.resolve("Ronin.nfo"), Instant.parse("2026-10-20T08:00:00Z"));
        MovieNfo heatContent = mock(MovieNfo.class);
        givenScannerFinds(heat, ronin);
        when(synchronizer.knownNfos(SOURCE_ID))
                .thenReturn(Map.of(heat.file().toString(), Instant.parse("2026-10-01T08:00:00Z")));
        when(nfoFiles.find(CompanionFiles.of(heat.file(), DiscFormat.FILE, 1))).thenReturn(Optional.of(heatNfo));
        when(nfoFiles.find(CompanionFiles.of(ronin.file(), DiscFormat.FILE, 1))).thenReturn(Optional.of(roninNfo));
        when(nfoFiles.read(heatNfo)).thenReturn(heatContent);
        when(nfoFiles.read(roninNfo)).thenThrow(mock(NfoException.class));
        ArgumentCaptor<List<ScannedMovie>> scanned = ArgumentCaptor.forClass(List.class);
        when(synchronizer.synchronize(eq(SOURCE_ID), scanned.capture()))
                .thenReturn(new ScanResult(SOURCE_ID, 1, 1, 0, 0, 1));

        service.scan(source, directory, new RecordingContext(false));

        assertThat(scanned.getValue()).extracting(ScannedMovie::candidate).containsExactly(heat, ronin);
        assertThat(scanned.getValue().getFirst().loaded()).hasValueSatisfying(
                loaded -> assertThat(loaded.content()).isSameAs(heatContent));
        assertThat(scanned.getValue().getLast().loaded()).hasValueSatisfying(
                loaded -> assertThatThrownBy(loaded::content).isInstanceOf(NfoException.class));
    }

    @Test
    void stopsBeforeTouchingLibraryWhenCancelled() {
        MediaSource source = givenSource(MediaType.MOVIE, directory);
        givenScannerFinds(candidate("Heat.mkv"));

        assertThatExceptionOfType(JobCancelledException.class)
                .isThrownBy(() -> service.scan(source, directory, new RecordingContext(true)));
        verify(synchronizer, never()).synchronize(anyLong(), anyList());
    }

    @Test
    void rejectsSourceWithOtherMediaTypeBeforeStartingJob() {
        givenSource(MediaType.TV_SHOW, directory);

        assertThatThrownBy(() -> service.startScan(SOURCE_ID))
                .isInstanceOfSatisfying(SourceNotScannableException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(exception.getBody().getDetail()).contains("keine Filme");
                });
        verify(jobService, never()).submit(anyString(), anyString(), any());
    }

    @Test
    void rejectsUnreachableDirectoryBeforeStartingJob() {
        givenSource(MediaType.MOVIE, directory.resolve("offline"));

        assertThatThrownBy(() -> service.startScan(SOURCE_ID))
                .isInstanceOfSatisfying(SourceNotScannableException.class,
                        exception -> assertThat(exception.getBody().getDetail()).contains("nicht erreichbar"));
        verify(jobService, never()).submit(anyString(), anyString(), any());
    }

    @Test
    void reportsScannerFailureAsUnreachable() {
        MediaSource source = givenSource(MediaType.MOVIE, directory);
        when(scanner.scan(eq(directory), any())).thenThrow(new UncheckedIOException(new IOException("network lost")));

        assertThatThrownBy(() -> service.scan(source, directory, new RecordingContext(false)))
                .isInstanceOf(SourceNotScannableException.class);
        verify(synchronizer, never()).synchronize(anyLong(), anyList());
    }

    /** Der Scanner findet die Filme in einem einzigen Unterordner. */
    private void givenScannerFinds(MovieCandidate... candidates) {
        when(scanner.scan(eq(directory), any())).thenAnswer(invocation -> {
            MovieDirectoryScanner.SubtreeScanner subtrees = invocation.getArgument(1);
            return subtrees.scanAll(List.of(directory.resolve("Filme")), subdirectory -> List.of(candidates))
                    .getFirst();
        });
    }

    private MovieCandidate candidate(String fileName) {
        return new MovieCandidate(directory.resolve(fileName), fileName, null, DiscFormat.FILE, 1);
    }

    private MediaSource givenSource(MediaType mediaType, Path path) {
        MediaSource source = mock(MediaSource.class);
        lenient().when(source.getId()).thenReturn(SOURCE_ID);
        lenient().when(source.getName()).thenReturn("Filme");
        lenient().when(source.getMediaType()).thenReturn(mediaType);
        lenient().when(source.getPath()).thenReturn(path.toString());
        lenient().when(sourceService.require(SOURCE_ID)).thenReturn(source);
        return source;
    }

    /** Merkt sich alle Fortschrittsmeldungen als „erledigt/gesamt Nachricht“. */
    private static final class RecordingContext implements JobContext {

        private final boolean cancelled;
        private final List<String> progress = new ArrayList<>();

        private RecordingContext(boolean cancelled) {
            this.cancelled = cancelled;
        }

        @Override
        public void reportProgress(int done, int total, String message) {
            progress.add(done + "/" + total + " " + message);
        }

        @Override
        public boolean isCancellationRequested() {
            return cancelled;
        }
    }
}
