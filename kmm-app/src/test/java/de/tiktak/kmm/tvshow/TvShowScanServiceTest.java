package de.tiktak.kmm.tvshow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import de.tiktak.kmm.job.JobCancelledException;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.scanner.EpisodeCandidate;
import de.tiktak.kmm.scanner.EpisodeNumber;
import de.tiktak.kmm.scanner.TvShowCandidate;
import de.tiktak.kmm.scanner.TvShowDirectoryScanner;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;

@ExtendWith(MockitoExtension.class)
class TvShowScanServiceTest {

    @Mock
    private MediaSourceService sourceService;

    @Mock
    private TvShowScannerFactory scannerFactory;

    @Mock
    private TvShowDirectoryScanner scanner;

    @Mock
    private TvShowLibrarySynchronizer synchronizer;

    @Mock
    private TvShowFolderReader folderReader;

    @Mock
    private JobService jobService;

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private TvShowScanService service;

    @TempDir
    Path directory;

    @Test
    void startsTheScanAsJobKeyedBySource() {
        givenSource(MediaType.TV_SHOW, directory);

        service.startScan(3);

        verify(jobService).submit(eq("scan:3"), eq("Scan „Serien“"), any());
    }

    @Test
    void rejectsMovieSourcesAndUnreachableDirectories() {
        givenSource(MediaType.MOVIE, directory);
        assertThatThrownBy(() -> service.startScan(3)).isInstanceOfSatisfying(TvShowProblemException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));

        givenSource(MediaType.TV_SHOW, directory.resolve("fehlt"));
        assertThatThrownBy(() -> service.startScan(3)).isInstanceOf(TvShowProblemException.class);
        verify(jobService, never()).submit(any(), any(), any());
    }

    @Test
    void readsShowsInParallelKeepsTheirOrderAndListsAtMostAHundredUnrecognizedFiles() {
        MediaSource source = givenSource(MediaType.TV_SHOW, directory);
        KnownNfos known = new KnownNfos(Map.of("x", Instant.EPOCH), Map.of());
        List<Path> folders = IntStream.range(0, 20).mapToObj(index -> directory.resolve("Serie " + index)).toList();
        when(scannerFactory.create()).thenReturn(scanner);
        when(scanner.showDirectories(directory)).thenReturn(folders);
        when(synchronizer.knownNfos(3)).thenReturn(known);
        when(folderReader.read(eq(scanner), any(), eq(known))).thenAnswer(invocation -> {
            Path folder = invocation.getArgument(1);
            if (folder.endsWith("Serie 5")) {
                return Optional.empty();
            }
            List<Path> unrecognized = IntStream.range(0, 10).mapToObj(index -> folder.resolve("x" + index + ".avi"))
                    .toList();
            EpisodeCandidate episode = new EpisodeCandidate(folder.resolve("S01E01.avi"),
                    new EpisodeNumber(1, List.of(1)), null);
            return Optional.of(new ScannedTvShow(new TvShowCandidate(folder, "x", null, List.of(episode),
                    unrecognized), Optional.empty(), Map.of(), Optional.empty(), Map.of()));
        });
        TvShowScanResult expected = new TvShowScanResult(3, 19, 0, 19, 0, 0, 0, 0, List.of());
        when(synchronizer.synchronize(eq(3L), anyList(), anyList())).thenReturn(expected);
        JobContext context = mock(JobContext.class);

        assertThat(service.scan(source, directory, context)).isEqualTo(expected);
        verify(events).publishEvent(new TvShowSourceScannedEvent(3, 38));
        verify(context).reportProgress(20, 20, TvShowScanService.SYNCHRONIZING_MESSAGE);
        verify(context, times(20)).reportProgress(intThat(done -> done >= 1 && done <= 20), eq(20), startsWith("Serie "));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScannedTvShow>> shows = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> files = ArgumentCaptor.forClass(List.class);
        verify(synchronizer).synchronize(eq(3L), shows.capture(), files.capture());
        assertThat(shows.getValue()).extracting(ScannedTvShow::path).containsExactlyElementsOf(folders.stream()
                .filter(folder -> !folder.endsWith("Serie 5")).map(Path::toString).toList());
        assertThat(files.getValue()).hasSize(TvShowScanService.MAX_LISTED_FILES).first()
                .isEqualTo(Path.of("Serie 0", "x0.avi").toString());
    }

    @Test
    void stopsWhenCancelledAndReportsUnreadableSources() {
        MediaSource source = givenSource(MediaType.TV_SHOW, directory);
        when(scannerFactory.create()).thenReturn(scanner);
        when(scanner.showDirectories(directory)).thenReturn(List.of(directory.resolve("x")));
        when(synchronizer.knownNfos(3)).thenReturn(KnownNfos.NONE);
        JobContext cancelled = mock(JobContext.class);
        when(cancelled.isCancellationRequested()).thenReturn(true);
        doThrow(new JobCancelledException()).when(cancelled).throwIfCancellationRequested();

        assertThatExceptionOfType(JobCancelledException.class)
                .isThrownBy(() -> service.scan(source, directory, cancelled));
        verify(folderReader, never()).read(any(), any(), any());
        verify(synchronizer, never()).synchronize(anyLong(), anyList(), anyList());

        when(scanner.showDirectories(directory)).thenThrow(new UncheckedIOException(new IOException("weg")));
        assertThatThrownBy(() -> service.scan(source, directory, mock(JobContext.class)))
                .isInstanceOf(TvShowProblemException.class);
    }

    @Test
    void passesOnFailuresWhileReadingAShow() {
        MediaSource source = givenSource(MediaType.TV_SHOW, directory);
        when(scannerFactory.create()).thenReturn(scanner);
        when(scanner.showDirectories(directory)).thenReturn(List.of(directory.resolve("x")));
        when(synchronizer.knownNfos(3)).thenReturn(KnownNfos.NONE);
        when(folderReader.read(any(), any(), any())).thenThrow(new IllegalStateException("kaputt"));

        assertThatThrownBy(() -> service.scan(source, directory, mock(JobContext.class)))
                .isInstanceOf(IllegalStateException.class).hasMessage("kaputt");
        verify(synchronizer, never()).synchronize(anyLong(), anyList(), anyList());
    }

    private MediaSource givenSource(MediaType type, Path path) {
        MediaSource source = new MediaSource("Serien", path.toString(), type);
        ReflectionTestUtils.setField(source, "id", 3L);
        lenient().when(sourceService.require(3)).thenReturn(source);
        return source;
    }
}
