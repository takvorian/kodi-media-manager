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
import de.tiktak.kmm.nfo.TvShowNfo;

@ExtendWith(MockitoExtension.class)
class TvNfoSyncTest {

    private static final Path SHOW_DIRECTORY = Path.of("serien", "ALF").toAbsolutePath();
    private static final Path EPISODE_FILE = SHOW_DIRECTORY.resolve("ALF - S01E01-E02.mkv");
    private static final Path EPISODE_NFO = SHOW_DIRECTORY.resolve("ALF - S01E01-E02.nfo");
    private static final Instant SYNCHRONIZED = Instant.parse("2026-10-27T12:00:00Z");
    private static final Instant CHANGED = Instant.parse("2026-10-28T12:00:00Z");
    private static final String TRAILER = "plugin://plugin.video.youtube/?action=play_video&videoid=alfTrailer1";

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

    @Test
    void readsAShowNfoBeforeTheFirstWriteWhenTheDataCameFromEmber() {
        TvShow show = new TvShow(1L, SHOW_DIRECTORY.toString(), "ALF", 1986);
        show.markNfoSynchronized(SYNCHRONIZED);
        TvNfoState unchanged = new TvNfoState(SHOW_DIRECTORY.resolve("tvshow.nfo"), SYNCHRONIZED);
        when(nfoFiles.findShowNfo(SHOW_DIRECTORY)).thenReturn(Optional.of(unchanged));
        when(nfoFiles.readShow(unchanged)).thenReturn(new TvShowNfo("ALF", null, null, null, null, null, "Germany:6",
                null, null, null, null, Map.of(), null, null, null, null));

        nfoSync.refreshShowIfChangedOutside(show);
        nfoSync.refreshShowIfChangedOutside(show);

        assertThat(show.metadata().mpaa()).isEqualTo("Germany:6");
        assertThat(show.isNfoRead()).isTrue();
        verify(nfoFiles).readShow(unchanged);
    }

    @Test
    void takesTheTrailerFromTheShowNfo() {
        TvShow show = new TvShow(1L, SHOW_DIRECTORY.toString(), "ALF", 1986);

        TvNfoSync.applyShow(show, showNfo().withTrailer(TRAILER), "ALF", 1986);
        assertThat(show.getTrailer()).isEqualTo(TRAILER);

        TvNfoSync.applyShow(show, showNfo(), "ALF", 1986);
        assertThat(show.getTrailer()).as("unbekannt bleibt wie bisher").isEqualTo(TRAILER);

        TvNfoSync.applyShow(show, showNfo().withTrailer(TvShowNfo.NO_TRAILER), "ALF", 1986);
        assertThat(show.getTrailer()).isNull();
        assertThat(show.nfoTrailer()).isEqualTo(TvShowNfo.NO_TRAILER);
    }

    @Test
    void readsAnUnknownTrailerFromTheNfoOnce() {
        TvShow show = new TvShow(1L, SHOW_DIRECTORY.toString(), "ALF", 1986);
        TvNfoState nfo = new TvNfoState(SHOW_DIRECTORY.resolve("tvshow.nfo"), SYNCHRONIZED);
        when(nfoFiles.findShowNfo(SHOW_DIRECTORY)).thenReturn(Optional.of(nfo));
        when(nfoFiles.readShow(nfo)).thenReturn(showNfo().withTrailer(TRAILER));

        assertThat(show.nfoTrailer()).isNull();
        assertThat(nfoSync.readTrailerIfUnknown(show)).isTrue();
        assertThat(nfoSync.readTrailerIfUnknown(show)).isTrue();

        assertThat(show.getTrailer()).isEqualTo(TRAILER);
        verify(nfoFiles).readShow(nfo);
    }

    @Test
    void treatsAShowWithoutNfoAsHavingNoTrailerAndKeepsUnreadableOnesUnknown() {
        TvShow withoutNfo = new TvShow(1L, SHOW_DIRECTORY.toString(), "ALF", 1986);
        when(nfoFiles.findShowNfo(SHOW_DIRECTORY)).thenReturn(Optional.empty());

        assertThat(nfoSync.readTrailerIfUnknown(withoutNfo)).isTrue();
        assertThat(withoutNfo.nfoTrailer()).isEqualTo(TvShowNfo.NO_TRAILER);

        TvShow unreadable = new TvShow(1L, SHOW_DIRECTORY.toString(), "ALF", 1986);
        TvNfoState nfo = new TvNfoState(SHOW_DIRECTORY.resolve("tvshow.nfo"), SYNCHRONIZED);
        when(nfoFiles.findShowNfo(SHOW_DIRECTORY)).thenReturn(Optional.of(nfo));
        when(nfoFiles.readShow(nfo)).thenThrow(mock(NfoException.class));

        assertThat(nfoSync.readTrailerIfUnknown(unreadable)).isFalse();
        assertThat(unreadable.isTrailerKnown()).isFalse();
    }

    private static TvShowNfo showNfo() {
        return new TvShowNfo("ALF", null, null, null, null, null, null, null, null, null, null, Map.of(), null, null,
                null, null);
    }

    private static EpisodeNfo episode(int number, String title, Integer playCount) {
        return new EpisodeNfo(title, null, 1, number, null, null, null, null, Map.of(), List.of(), List.of(),
                List.of(), List.of(), null, playCount, null, null, StreamDetails.EMPTY);
    }
}
