package de.tiktak.kmm.tvshow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import de.tiktak.kmm.nfo.EpisodeNfo;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.nfo.StreamDetails;

@ExtendWith(MockitoExtension.class)
class TvNfoSyncTest {

    private static final Path SHOW_DIRECTORY = Path.of("serien", "ALF").toAbsolutePath();
    private static final Path EPISODE_FILE = SHOW_DIRECTORY.resolve("ALF - S01E01-E02.mkv");
    private static final Path EPISODE_NFO = SHOW_DIRECTORY.resolve("ALF - S01E01-E02.nfo");
    private static final Instant SYNCHRONIZED = Instant.parse("2026-10-27T12:00:00Z");
    private static final Instant CHANGED = Instant.parse("2026-10-28T12:00:00Z");

    @Mock
    private TvShowNfoFiles nfoFiles;

    private TvNfoSync nfoSync;
    private Episode first;
    private Episode second;

    @BeforeEach
    void setUp() {
        nfoSync = new TvNfoSync(nfoFiles, Clock.fixed(CHANGED, ZoneOffset.UTC));
        first = new Episode(1L, EPISODE_FILE.toString(), 1, 1, "Teil 1");
        second = new Episode(1L, EPISODE_FILE.toString(), 1, 2, "Teil 2");
        first.markNfoSynchronized(SYNCHRONIZED);
        second.markNfoSynchronized(SYNCHRONIZED);
    }

    @Test
    void readsAnEpisodeNfoChangedOutsideKmmForAllEpisodesOfTheFile() {
        TvNfoState changed = new TvNfoState(EPISODE_NFO, CHANGED);
        when(nfoFiles.findEpisodeNfo(EPISODE_FILE)).thenReturn(Optional.of(changed));
        when(nfoFiles.readEpisodes(changed)).thenReturn(List.of(episode(1, "Heimweh", 4), episode(2, "Neu", null)));

        nfoSync.refreshEpisodesIfChangedOutside(List.of(first, second));

        assertThat(first.getTitle()).isEqualTo("Heimweh");
        assertThat(first.extras().playCount()).isEqualTo(4);
        assertThat(second.getTitle()).isEqualTo("Neu");
        assertThat(first.getNfoModifiedAt()).isEqualTo(CHANGED);
        assertThat(second.getNfoModifiedAt()).isEqualTo(CHANGED);
    }

    @Test
    void leavesEpisodesAloneIfTheirNfoIsUnchanged() {
        when(nfoFiles.findEpisodeNfo(EPISODE_FILE)).thenReturn(Optional.of(new TvNfoState(EPISODE_NFO, SYNCHRONIZED)));

        nfoSync.refreshEpisodesIfChangedOutside(List.of(first, second));

        verify(nfoFiles, never()).readEpisodes(any());
    }

    @Test
    void readsAShowNfoChangedOutsideKmmAndRefusesAnUnreadableOne() {
        TvShow show = new TvShow(1L, SHOW_DIRECTORY.toString(), "ALF", 1986);
        show.markNfoSynchronized(SYNCHRONIZED);
        TvNfoState changed = new TvNfoState(SHOW_DIRECTORY.resolve("tvshow.nfo"), CHANGED);
        when(nfoFiles.findShowNfo(SHOW_DIRECTORY)).thenReturn(Optional.of(changed));
        when(nfoFiles.readShow(changed)).thenThrow(mock(NfoException.class));

        assertThatThrownBy(() -> nfoSync.refreshShowIfChangedOutside(show))
                .isInstanceOfSatisfying(TvShowProblemException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(show.getNfoModifiedAt()).isEqualTo(SYNCHRONIZED);
    }

    private static EpisodeNfo episode(int number, String title, Integer playCount) {
        return new EpisodeNfo(title, null, 1, number, null, null, null, null, Map.of(), List.of(), List.of(),
                List.of(), List.of(), null, playCount, null, null, StreamDetails.EMPTY);
    }
}
