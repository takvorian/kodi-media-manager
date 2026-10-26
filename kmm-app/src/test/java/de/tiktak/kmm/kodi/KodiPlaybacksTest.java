package de.tiktak.kmm.kodi;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.kodi.rpc.KodiPlayback;

class KodiPlaybacksTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final LocalDateTime PLAYED = LocalDateTime.of(2014, 4, 18, 13, 25, 31);
    private static final Instant PLAYED_AT = PLAYED.atZone(BERLIN).toInstant();

    @Test
    void treatsAMissingPlayCountAsUnwatched() {
        assertThat(KodiPlaybacks.isCurrent(null, null, kodi(0, null), BERLIN)).isTrue();
        assertThat(KodiPlaybacks.isCurrent(0, null, kodi(0, null), BERLIN)).isTrue();
        assertThat(KodiPlaybacks.isCurrent(2, null, kodi(0, null), BERLIN)).isFalse();
        assertThat(KodiPlaybacks.isCurrent(null, null, kodi(1, null), BERLIN)).isFalse();
    }

    @Test
    void keepsTheExactTimeWhenKodiOnlyKnowsTheDate() {
        assertThat(KodiPlaybacks.isCurrent(1, PLAYED_AT, kodi(1, PLAYED), BERLIN)).isTrue();
        assertThat(KodiPlaybacks.isCurrent(1, PLAYED_AT, kodi(1, PLAYED.toLocalDate().atStartOfDay()), BERLIN))
                .isTrue();
        assertThat(KodiPlaybacks.isCurrent(1, PLAYED_AT, kodi(1, PLAYED.plusDays(1).toLocalDate().atStartOfDay()),
                BERLIN)).isFalse();
        assertThat(KodiPlaybacks.isCurrent(1, PLAYED_AT, kodi(1, PLAYED.plusHours(1)), BERLIN)).isFalse();
        assertThat(KodiPlaybacks.isCurrent(1, null, kodi(1, PLAYED), BERLIN)).isFalse();
        assertThat(KodiPlaybacks.isCurrent(1, PLAYED_AT, kodi(1, null), BERLIN)).isFalse();
    }

    @Test
    void convertsKodisLocalTime() {
        assertThat(KodiPlaybacks.lastPlayedAt(kodi(1, PLAYED), BERLIN)).isEqualTo(PLAYED_AT);
        assertThat(KodiPlaybacks.lastPlayedAt(kodi(0, null), BERLIN)).isNull();
    }

    @Test
    void findsPlaybacksByFileIgnoringCaseAndStacks() {
        KodiPlayback stacked = new KodiPlayback("stack://smb://nas/A/a CD1.avi , smb://nas/A/a CD2.avi", null, null,
                1, null);
        KodiPlayback first = new KodiPlayback("smb://nas/S/s.mkv", 1, 1, 0, null);
        KodiPlayback second = new KodiPlayback("smb://nas/S/s.mkv", 1, 2, 3, null);
        KodiPlaybackIndex index = new KodiPlaybackIndex(List.of(stacked, first, second));

        assertThat(index.find("SMB://nas/a/a cd1.avi", playback -> true)).contains(stacked);
        assertThat(index.find("smb://nas/S/s.mkv", playback -> playback.isEpisode("smb://nas/S/s.mkv", 1, 2)))
                .contains(second);
        assertThat(index.find("smb://nas/fehlt.mkv", playback -> true)).isEmpty();
    }

    private static KodiPlayback kodi(int playCount, LocalDateTime lastPlayed) {
        return new KodiPlayback("smb://nas/x.mkv", null, null, playCount, lastPlayed);
    }
}
