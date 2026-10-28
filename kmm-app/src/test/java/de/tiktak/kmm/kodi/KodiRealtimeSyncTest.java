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
import de.tiktak.kmm.movie.MovieRenamedEvent;
import de.tiktak.kmm.movie.MovieSetChangedEvent;
import de.tiktak.kmm.movie.MovieSourceScannedEvent;
import de.tiktak.kmm.tvshow.EpisodeFilesChangedEvent;
import de.tiktak.kmm.tvshow.TvShowFilesChangedEvent;
import de.tiktak.kmm.tvshow.TvShowRenamedEvent;
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
    void syncsEveryRenamedMovie() {
        MovieRenamedEvent first = new MovieRenamedEvent(5, 3, "/filme/heat", "/filme/Heat (1995)");
        MovieRenamedEvent second = new MovieRenamedEvent(5, 3, "/filme/Heat (1995)", "/filme/Heat");

        realtimeSync.onMovieRenamed(first);
        realtimeSync.onMovieRenamed(second);

        verify(syncService, timeout(WAIT_MILLIS)).syncRenamedMovieOnRealtimeHosts(first);
        verify(syncService, timeout(WAIT_MILLIS)).syncRenamedMovieOnRealtimeHosts(second);
    }

    @Test
    void syncsChangedMovieSets() {
        MovieSetChangedEvent event = new MovieSetChangedEvent(4, "Alt");

        realtimeSync.onMovieSetChanged(event);

        verify(syncService, timeout(WAIT_MILLIS)).syncMovieSetOnRealtimeHosts(event);
    }

    @Test
    void syncsEveryRenamedTvShow() {
        TvShowRenamedEvent event = new TvShowRenamedEvent(7, 2, "/serien/lost", "/serien/Lost (2004)");

        realtimeSync.onTvShowRenamed(event);

        verify(syncService, timeout(WAIT_MILLIS)).syncRenamedTvShowOnRealtimeHosts(event);
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
