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
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import de.tiktak.kmm.plugin.api.MediaType;
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

    @InjectMocks
    private MovieScanService service;

    @TempDir
    Path directory;

    @Test
    void scansDirectoryAndSynchronizesResult() {
        givenSource(MediaType.MOVIE, directory);
        var candidates = List.of(new MovieCandidate(directory.resolve("Heat.mkv"), "Heat", 1995, DiscFormat.FILE, 1));
        var expected = new ScanResult(SOURCE_ID, 1, 0, 0);
        when(scanner.scan(directory)).thenReturn(candidates);
        when(synchronizer.synchronize(SOURCE_ID, candidates)).thenReturn(expected);

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
