package de.tiktak.kmm.kodi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.LongPredicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobCancelledException;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobTask;
import de.tiktak.kmm.movie.MovieBatchService;
import de.tiktak.kmm.tvshow.TvShowBatchService;
import de.tiktak.kmm.tvshow.TvShowDetails;
import de.tiktak.kmm.tvshow.TvShowMetadata;
import de.tiktak.kmm.tvshow.TvShowMetadataService;

@ExtendWith(MockitoExtension.class)
class KodiBatchRefreshServiceTest {

    private static final LocalizedText MOVIES_TITLE = LocalizedText.of("kodi.batch.refreshMovies");
    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");
    private static final KodiTarget LIVING_ROOM = new KodiTarget(1, "Wohnzimmer", null, null, false);
    private static final KodiTarget KITCHEN = new KodiTarget(2, "Küche", null, null, false);

    @Mock
    private KodiHostService hostService;

    @Mock
    private KodiSyncService syncService;

    @Mock
    private MovieBatchService movieBatch;

    @Mock
    private TvShowBatchService showBatch;

    @Mock
    private TvShowMetadataService tvShowService;

    @Mock
    private JobService jobService;

    @Mock
    private JobContext context;

    @Captor
    private ArgumentCaptor<LongPredicate> change;

    @Captor
    private ArgumentCaptor<JobTask<?>> task;

    private KodiBatchRefreshService service;

    @BeforeEach
    void setUp() {
        service = new KodiBatchRefreshService(hostService, syncService, movieBatch, showBatch, tvShowService,
                jobService);
    }

    @Test
    void rejectsASelectionWithoutAnyHost() {
        when(hostService.targets()).thenReturn(List.of());

        assertThatExceptionOfType(KodiProblemException.class)
                .isThrownBy(() -> service.startMovies(List.of(5L)))
                .satisfies(e -> assertThat(e.localizedText()).isEqualTo(LocalizedText.of("kodi.noHosts.detail")));
        verify(movieBatch, never()).startFill(any(LocalizedText.class), anyList(), any());
    }

    @Test
    void countsARefreshedMovieAsChanged() {
        LongPredicate refresh = movieChange(LIVING_ROOM);
        when(syncService.refreshMovie(5, List.of(LIVING_ROOM)))
                .thenReturn(List.of(result(LIVING_ROOM, KodiSyncOutcome.REFRESHED)));

        assertThat(refresh.test(5)).isTrue();
    }

    @Test
    void countsAScanAsChangedAndAnUnknownEntryAsUnchanged() {
        LongPredicate refresh = movieChange(LIVING_ROOM);
        when(syncService.refreshMovie(5, List.of(LIVING_ROOM)))
                .thenReturn(List.of(result(LIVING_ROOM, KodiSyncOutcome.SCAN_STARTED)));
        when(syncService.refreshMovie(6, List.of(LIVING_ROOM)))
                .thenReturn(List.of(result(LIVING_ROOM, KodiSyncOutcome.NOT_IN_KODI)));

        assertThat(refresh.test(5)).isTrue();
        assertThat(refresh.test(6)).isFalse();
    }

    @Test
    void reportsAMissingPathMappingButKeepsTheHost() {
        LongPredicate refresh = movieChange(LIVING_ROOM);
        when(syncService.refreshMovie(anyLong(), eq(List.of(LIVING_ROOM))))
                .thenReturn(List.of(result(LIVING_ROOM, KodiSyncOutcome.NOT_MAPPED)));

        assertThatExceptionOfType(KodiProblemException.class).isThrownBy(() -> refresh.test(5))
                .satisfies(e -> assertThat(e.getBody().getDetail()).isEqualTo("Wohnzimmer: NOT_MAPPED"));
        assertThatExceptionOfType(KodiProblemException.class).isThrownBy(() -> refresh.test(6));
        verify(syncService).refreshMovie(6, List.of(LIVING_ROOM));
    }

    @Test
    void skipsAHostThatDoesNotAnswerForTheRestOfTheRun() {
        LongPredicate refresh = movieChange(LIVING_ROOM, KITCHEN);
        when(syncService.refreshMovie(5, List.of(LIVING_ROOM, KITCHEN))).thenReturn(List.of(
                result(LIVING_ROOM, KodiSyncOutcome.FAILED), result(KITCHEN, KodiSyncOutcome.REFRESHED)));
        when(syncService.refreshMovie(6, List.of(KITCHEN)))
                .thenReturn(List.of(result(KITCHEN, KodiSyncOutcome.REFRESHED)));

        assertThatExceptionOfType(KodiProblemException.class).isThrownBy(() -> refresh.test(5));

        assertThat(refresh.test(6)).isTrue();
    }

    @Test
    void failsFastOnceNoHostIsLeft() {
        LongPredicate refresh = movieChange(LIVING_ROOM);
        when(syncService.refreshMovie(5, List.of(LIVING_ROOM)))
                .thenReturn(List.of(result(LIVING_ROOM, KodiSyncOutcome.FAILED)));
        assertThatExceptionOfType(KodiProblemException.class).isThrownBy(() -> refresh.test(5));

        assertThatExceptionOfType(KodiProblemException.class).isThrownBy(() -> refresh.test(6))
                .satisfies(e -> assertThat(e.getBody().getDetail())
                        .isEqualTo("Skipped because no Kodi device answers any more."));
        verify(syncService, never()).refreshMovie(eq(6L), anyList());
    }

    @Test
    void refreshesTvShowsWithTheirEpisodes() {
        when(hostService.targets()).thenReturn(List.of(LIVING_ROOM));
        service.startTvShows(List.of(7L));
        verify(showBatch).startFill(eq(LocalizedText.of("kodi.batch.refreshTvShows")), eq(List.of(7L)), change.capture());
        when(syncService.refreshTvShowWithEpisodes(7, OptionalInt.empty(), List.of(LIVING_ROOM), KodiProgress.NONE))
                .thenReturn(List.of(result(LIVING_ROOM, KodiSyncOutcome.REFRESHED)));

        assertThat(change.getValue().test(7)).isTrue();
    }

    @Test
    void refreshesATvShowAsAJobOfItsOwnWithTheResultsOfAllHosts() {
        givenShow("Lost");
        List<KodiSyncResult> results = List.of(result(LIVING_ROOM, KodiSyncOutcome.REFRESHED));
        when(syncService.refreshTvShowWithEpisodes(eq(7L), eq(OptionalInt.empty()), eq(List.of(LIVING_ROOM)), any()))
                .thenReturn(results);

        service.startTvShow(7, OptionalInt.empty());

        verify(jobService).submit(eq("kodi-tvshow:7"),
                eq(LocalizedText.of("kodi.job.refreshTvShow").with("title", "Lost")), task.capture());
        assertThat(task.getValue().run(context)).isEqualTo(results);
    }

    @Test
    void refreshesASeasonAndReportsEveryEpisode() {
        givenShow("Lost");
        doAnswer(invocation -> {
            KodiProgress progress = invocation.getArgument(3);
            progress.report(1, 2);
            progress.report(2, 2);
            return List.of();
        }).when(syncService).refreshTvShowWithEpisodes(eq(7L), eq(OptionalInt.of(2)), anyList(), any());

        service.startTvShow(7, OptionalInt.of(2));

        verify(jobService).submit(eq("kodi-tvshow:7"),
                eq(LocalizedText.of("kodi.job.refreshSeason").with("title", "Lost").with("season", 2)),
                task.capture());
        task.getValue().run(context);
        verify(context).reportProgress(1, 2);
        verify(context).reportProgress(2, 2);
    }

    @Test
    void stopsBetweenEpisodesWhenTheJobIsCancelled() {
        givenShow("Lost");
        doAnswer(invocation -> {
            invocation.<KodiProgress>getArgument(3).report(1, 2);
            return List.of();
        }).when(syncService).refreshTvShowWithEpisodes(anyLong(), any(), anyList(), any());
        doAnswer(invocation -> {
            throw new JobCancelledException();
        }).when(context).throwIfCancellationRequested();
        service.startTvShow(7, OptionalInt.empty());
        verify(jobService).submit(anyString(), any(LocalizedText.class), task.capture());

        assertThatExceptionOfType(JobCancelledException.class).isThrownBy(() -> task.getValue().run(context));
        verify(context, never()).reportProgress(1, 2);
    }

    @Test
    void scansTvShowsAsABatch() {
        when(hostService.targets()).thenReturn(List.of(LIVING_ROOM));
        service.startTvShowScans(List.of(7L));
        verify(showBatch).startFill(eq(LocalizedText.of("kodi.batch.scanTvShows")), eq(List.of(7L)), change.capture());
        when(syncService.scanTvShow(7, OptionalInt.empty(), List.of(LIVING_ROOM), KodiProgress.NONE))
                .thenReturn(List.of(result(LIVING_ROOM, KodiSyncOutcome.SCAN_STARTED)));

        assertThat(change.getValue().test(7)).isTrue();
    }

    @Test
    void scansATvShowOrASeasonAsAJobOfItsOwn() {
        givenShow("Lost");
        List<KodiSyncResult> results = List.of(result(LIVING_ROOM, KodiSyncOutcome.SCAN_STARTED));
        doAnswer(invocation -> {
            invocation.<KodiProgress>getArgument(3).report(1, 1);
            return results;
        }).when(syncService).scanTvShow(eq(7L), any(), eq(List.of(LIVING_ROOM)), any());

        service.startTvShowScan(7, OptionalInt.empty());
        service.startTvShowScan(7, OptionalInt.of(2));

        verify(jobService).submit(eq("kodi-tvshow:7"),
                eq(LocalizedText.of("kodi.job.scanTvShow").with("title", "Lost")), task.capture());
        verify(jobService).submit(eq("kodi-tvshow:7"),
                eq(LocalizedText.of("kodi.job.scanSeason").with("title", "Lost").with("season", 2)), any());
        assertThat(task.getValue().run(context)).isEqualTo(results);
        verify(context).reportProgress(1, 1);
    }

    @Test
    void rejectsATvShowRefreshWithoutAnyHost() {
        when(hostService.targets()).thenReturn(List.of());

        assertThatExceptionOfType(KodiProblemException.class)
                .isThrownBy(() -> service.startTvShow(7, OptionalInt.empty()));
        verify(jobService, never()).submit(anyString(), any(LocalizedText.class), any());
    }

    private void givenShow(String title) {
        when(hostService.targets()).thenReturn(List.of(LIVING_ROOM));
        TvShowDetails show = mock(TvShowDetails.class);
        when(show.metadata()).thenReturn(new TvShowMetadata(title, null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), null, null, null, null));
        when(tvShowService.findShow(7)).thenReturn(show);
    }

    @Test
    void namesTheCountInTheJobTitle() {
        when(hostService.targets()).thenReturn(List.of(LIVING_ROOM));

        service.startMovies(List.of(5L, 6L));
        service.startMovies(List.of(5L));
        service.startTvShows(List.of(7L, 8L));

        verify(movieBatch).startFill(eq(MOVIES_TITLE), eq(List.of(5L, 6L)), any());
        verify(movieBatch).startFill(eq(MOVIES_TITLE), eq(List.of(5L)), any());
        verify(showBatch).startFill(eq(LocalizedText.of("kodi.batch.refreshTvShows")), eq(List.of(7L, 8L)), any());
    }

    private LongPredicate movieChange(KodiTarget... targets) {
        when(hostService.targets()).thenReturn(List.of(targets));
        service.startMovies(List.of(5L, 6L));
        verify(movieBatch).startFill(any(LocalizedText.class), anyList(), change.capture());
        return change.getValue();
    }

    private static KodiSyncResult result(KodiTarget target, KodiSyncOutcome outcome) {
        return new KodiSyncResult(target.id(), target.name(), outcome, LocalizedText.literal(outcome.name()), NOW);
    }
}
