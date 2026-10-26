package de.tiktak.kmm.kodi;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

import de.tiktak.kmm.kodi.rpc.KodiPlayback;

/**
 * Vergleicht den Gesehen-Status in KMM mit dem in Kodi.
 */
final class KodiPlaybacks {

    private KodiPlaybacks() {
    }

    /**
     * Gleich sind die Stände auch, wenn KMM keine Wiedergabezahl kennt und Kodi 0 meldet, und wenn Kodi
     * zur selben letzten Wiedergabe nur das Datum kennt: Aus NFOs übernimmt Kodi die Uhrzeit nicht, die
     * genauere Angabe in KMM soll dabei nicht verloren gehen.
     */
    static boolean isCurrent(Integer playCount, Instant lastPlayedAt, KodiPlayback kodi, ZoneId zone) {
        int known = playCount == null ? 0 : playCount;
        if (known != kodi.playCount()) {
            return false;
        }
        LocalDateTime local = lastPlayedAt == null ? null : LocalDateTime.ofInstant(lastPlayedAt, zone);
        LocalDateTime fromKodi = kodi.lastPlayed();
        if (Objects.equals(local, fromKodi)) {
            return true;
        }
        return local != null && fromKodi != null && fromKodi.toLocalTime().equals(LocalTime.MIDNIGHT)
                && fromKodi.toLocalDate().equals(local.toLocalDate());
    }

    static Instant lastPlayedAt(KodiPlayback kodi, ZoneId zone) {
        return kodi.lastPlayed() == null ? null : kodi.lastPlayed().atZone(zone).toInstant();
    }
}
