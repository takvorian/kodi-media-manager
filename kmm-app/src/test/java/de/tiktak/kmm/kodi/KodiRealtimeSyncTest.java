package de.tiktak.kmm.kodi;

import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tiktak.kmm.movie.MovieFilesChangedEvent;
import de.tiktak.kmm.movie.MovieSourceScannedEvent;
import de.tiktak.kmm.tvshow.EpisodeFilesChangedEvent;
import de.tiktak.kmm.tvshow.TvShowFilesChangedEvent;
import de.tiktak.kmm.tvshow.TvShowSourceScannedEvent;

@ExtendWith(MockitoExtension.class)
class KodiRealtimeSyncTest {

    private static final int WAIT_MILLIS = 2000;

    @Mock
    private KodiSyncService syncService;

    private KodiRealtimeSync realtimeSync;

    @BeforeEach
    void setUp() {
        realtimeSync = new KodiRealtimeSync(syncService,
                new KodiProperties(Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMillis(100)));
    }

    @AfterEach
    void tearDown() {
        realtimeSync.destroy();
    }

    @Test
    void refreshesAChangedMovieOnceAfterTheDelay() {
        realtimeSync.onMovieFilesChanged(new MovieFilesChangedEvent(5));
        realtimeSync.onMovieFilesChanged(new MovieFilesChangedEvent(5));

        verify(syncService, timeout(WAIT_MILLIS).times(1)).refreshMovieOnRealtimeHosts(5);
        verify(syncService, after(300).times(1)).refreshMovieOnRealtimeHosts(5);
    }

    @Test
    void scansASourceOnlyWhenTheScanFoundNewMovies() {
        realtimeSync.onSourceScanned(new MovieSourceScannedEvent(3, 0, 4));
        realtimeSync.onSourceScanned(new MovieSourceScannedEvent(4, 2, 0));

        verify(syncService, timeout(WAIT_MILLIS)).scanSourceOnRealtimeHosts(4);
        verify(syncService, never()).scanSourceOnRealtimeHosts(3);
    }

    @Test
    void refreshesChangedTvShowsAndEpisodesAndScansTvSourcesWithNewEntries() {
        realtimeSync.onTvShowFilesChanged(new TvShowFilesChangedEvent(7));
        realtimeSync.onTvShowFilesChanged(new TvShowFilesChangedEvent(7));
        realtimeSync.onEpisodeFilesChanged(new EpisodeFilesChangedEvent(12));
        realtimeSync.onTvSourceScanned(new TvShowSourceScannedEvent(5, 0));
        realtimeSync.onTvSourceScanned(new TvShowSourceScannedEvent(6, 3));

        verify(syncService, timeout(WAIT_MILLIS).times(1)).refreshTvShowOnRealtimeHosts(7);
        verify(syncService, timeout(WAIT_MILLIS)).refreshEpisodeOnRealtimeHosts(12);
        verify(syncService, timeout(WAIT_MILLIS)).scanSourceOnRealtimeHosts(6);
        verify(syncService, never()).scanSourceOnRealtimeHosts(5);
    }
}
