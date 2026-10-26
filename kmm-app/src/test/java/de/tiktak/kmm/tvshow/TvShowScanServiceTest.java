package de.tiktak.kmm.tvshow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
    private TvShowNfoFiles nfoFiles;

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
    void readsNfosAndListsAtMostAHundredUnrecognizedFiles() {
        MediaSource source = givenSource(MediaType.TV_SHOW, directory);
        Path episodeFile = directory.resolve("ALF/S01E01.avi");
        TvNfoState nfo = new TvNfoState(directory.resolve("ALF/S01E01.nfo"), Instant.EPOCH);
        List<Path> unrecognized = new ArrayList<>(IntStream.range(0, 150)
                .mapToObj(index -> directory.resolve("ALF/x" + index + ".avi")).toList());
        TvShowCandidate alf = new TvShowCandidate(directory.resolve("ALF"), "ALF", null,
                List.of(new EpisodeCandidate(episodeFile, new EpisodeNumber(1, List.of(1)), null)), unrecognized);
        when(scannerFactory.create()).thenReturn(scanner);
        when(scanner.scan(directory)).thenReturn(List.of(alf));
        when(nfoFiles.findEpisodeNfo(episodeFile)).thenReturn(Optional.of(nfo));
        when(nfoFiles.findShowNfo(alf.directory())).thenReturn(Optional.empty());
        TvShowScanResult expected = new TvShowScanResult(3, 1, 0, 1, 0, 0, 0, 0, List.of());
        when(synchronizer.synchronize(eq(3L), anyList(), anyList())).thenReturn(expected);

        assertThat(service.scan(source, directory, mock(JobContext.class))).isEqualTo(expected);
        verify(events).publishEvent(new TvShowSourceScannedEvent(3, 2));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScannedTvShow>> shows = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> files = ArgumentCaptor.forClass(List.class);
        verify(synchronizer).synchronize(eq(3L), shows.capture(), files.capture());
        assertThat(shows.getValue().getFirst().episodeNfos()).containsEntry(episodeFile, nfo);
        assertThat(files.getValue()).hasSize(TvShowScanService.MAX_LISTED_FILES).first()
                .isEqualTo(Path.of("ALF", "x0.avi").toString());
    }

    @Test
    void stopsWhenCancelledAndReportsUnreadableSources() {
        MediaSource source = givenSource(MediaType.TV_SHOW, directory);
        when(scannerFactory.create()).thenReturn(scanner);
        when(scanner.scan(directory)).thenReturn(List.of(new TvShowCandidate(directory, "x", null, List.of(),
                List.of())));
        JobContext cancelled = mock(JobContext.class);
        doThrow(new JobCancelledException()).when(cancelled).throwIfCancellationRequested();

        assertThatExceptionOfType(JobCancelledException.class)
                .isThrownBy(() -> service.scan(source, directory, cancelled));
        verify(synchronizer, never()).synchronize(anyLong(), anyList(), anyList());

        when(scanner.scan(directory)).thenThrow(new UncheckedIOException(new IOException("weg")));
        assertThatThrownBy(() -> service.scan(source, directory, mock(JobContext.class)))
                .isInstanceOf(TvShowProblemException.class);
    }

    private MediaSource givenSource(MediaType type, Path path) {
        MediaSource source = new MediaSource("Serien", path.toString(), type);
        ReflectionTestUtils.setField(source, "id", 3L);
        lenient().when(sourceService.require(3)).thenReturn(source);
        return source;
    }
}
