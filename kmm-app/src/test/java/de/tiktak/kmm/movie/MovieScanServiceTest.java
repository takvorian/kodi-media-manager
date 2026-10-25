package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

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
    private MovieLibrarySynchronizer synchronizer;

    @Mock
    private MovieNfoFiles nfoFiles;

    @InjectMocks
    private MovieScanService service;

    @TempDir
    Path directory;

    @Test
    void scansDirectoryLooksUpNfosAndSynchronizesResult() {
        givenSource(MediaType.MOVIE, directory);
        var heat = new MovieCandidate(directory.resolve("Heat.mkv"), "Heat", 1995, DiscFormat.FILE, 1);
        var nfo = new NfoFileState(directory.resolve("Heat.nfo"), Instant.parse("2026-10-20T08:00:00Z"));
        var expected = new ScanResult(SOURCE_ID, 1, 0, 0, 0, 0);
        when(scanner.scan(directory)).thenReturn(List.of(heat));
        when(nfoFiles.find(CompanionFiles.of(heat.file(), DiscFormat.FILE, 1))).thenReturn(Optional.of(nfo));
        when(synchronizer.synchronize(SOURCE_ID, List.of(new ScannedMovie(heat, Optional.of(nfo))))).thenReturn(expected);

        assertThat(service.scan(SOURCE_ID)).isEqualTo(expected);
    }

    @Test
    void rejectsSourceWithOtherMediaType() {
        givenSource(MediaType.TV_SHOW, directory);

        assertThatThrownBy(() -> service.scan(SOURCE_ID))
                .isInstanceOfSatisfying(SourceNotScannableException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(exception.getBody().getDetail()).contains("keine Filme");
                });
        verify(synchronizer, never()).synchronize(anyLong(), anyList());
    }

    @Test
    void rejectsUnreachableDirectoryWithoutTouchingLibrary() {
        givenSource(MediaType.MOVIE, directory.resolve("offline"));

        assertThatThrownBy(() -> service.scan(SOURCE_ID))
                .isInstanceOfSatisfying(SourceNotScannableException.class,
                        exception -> assertThat(exception.getBody().getDetail()).contains("nicht erreichbar"));
        verify(synchronizer, never()).synchronize(anyLong(), anyList());
    }

    @Test
    void reportsScannerFailureAsUnreachable() {
        givenSource(MediaType.MOVIE, directory);
        when(scanner.scan(directory)).thenThrow(new UncheckedIOException(new IOException("network lost")));

        assertThatThrownBy(() -> service.scan(SOURCE_ID)).isInstanceOf(SourceNotScannableException.class);
        verify(synchronizer, never()).synchronize(anyLong(), anyList());
    }

    private void givenSource(MediaType mediaType, Path path) {
        MediaSource source = mock(MediaSource.class);
        when(source.getName()).thenReturn("Filme");
        when(source.getMediaType()).thenReturn(mediaType);
        lenient().when(source.getPath()).thenReturn(path.toString());
        when(sourceService.require(SOURCE_ID)).thenReturn(source);
    }
}
