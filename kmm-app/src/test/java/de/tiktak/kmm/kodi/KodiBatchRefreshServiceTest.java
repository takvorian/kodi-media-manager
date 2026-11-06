package de.tiktak.kmm.kodi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.function.LongPredicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.movie.MovieBatchService;
import de.tiktak.kmm.tvshow.TvShowBatchService;

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

    @Captor
    private ArgumentCaptor<LongPredicate> change;

    private KodiBatchRefreshService service;

    @BeforeEach
    void setUp() {
        service = new KodiBatchRefreshService(hostService, syncService, movieBatch, showBatch);
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
    void refreshesTvShows() {
        when(hostService.targets()).thenReturn(List.of(LIVING_ROOM));
        service.startTvShows(List.of(7L));
        verify(showBatch).startFill(eq("1 Serie in Kodi aktualisieren"), eq(List.of(7L)), change.capture());
        when(syncService.refreshTvShow(7, List.of(LIVING_ROOM)))
                .thenReturn(List.of(result(LIVING_ROOM, KodiSyncOutcome.REFRESHED)));

        assertThat(change.getValue().test(7)).isTrue();
    }

    @Test
    void namesTheCountInTheJobTitle() {
        when(hostService.targets()).thenReturn(List.of(LIVING_ROOM));

        service.startMovies(List.of(5L, 6L));
        service.startMovies(List.of(5L));
        service.startTvShows(List.of(7L, 8L));

        verify(movieBatch).startFill(eq(MOVIES_TITLE), eq(List.of(5L, 6L)), any());
        verify(movieBatch).startFill(eq(MOVIES_TITLE), eq(List.of(5L)), any());
        verify(showBatch).startFill(eq("%d Serien in Kodi aktualisieren"), eq(List.of(7L, 8L)), any());
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
