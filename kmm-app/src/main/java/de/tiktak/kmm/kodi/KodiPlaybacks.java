package de.tiktak.kmm.kodi;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

import de.tiktak.kmm.kodi.rpc.KodiPlayback;

/**
 * Vergleicht den Gesehen-Status in KMM mit dem in Kodi und führt beide zusammen. Kodi ergänzt nur:
 * mehr Wiedergaben oder eine neuere letzte Wiedergabe. Ein „ungesehen“ in Kodi löscht nichts in KMM.
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

    /** @return der Stand in KMM, ergänzt um die höhere Wiedergabezahl und die neuere letzte Wiedergabe */
    static Playback merge(Integer playCount, Instant lastPlayedAt, KodiPlayback kodi, ZoneId zone) {
        int known = playCount == null ? 0 : playCount;
        Integer mergedCount = kodi.playCount() > known ? Integer.valueOf(kodi.playCount()) : playCount;
        Instant mergedLastPlayed = isNewer(kodi, lastPlayedAt, zone) ? lastPlayedAt(kodi, zone) : lastPlayedAt;
        return new Playback(mergedCount, mergedLastPlayed);
    }

    /** Ein Datum ohne Uhrzeit vom selben Tag ist nicht neuer, nur ungenauer. */
    private static boolean isNewer(KodiPlayback kodi, Instant lastPlayedAt, ZoneId zone) {
        LocalDateTime fromKodi = kodi.lastPlayed();
        if (fromKodi == null) {
            return false;
        }
        if (lastPlayedAt == null) {
            return true;
        }
        LocalDateTime local = LocalDateTime.ofInstant(lastPlayedAt, zone);
        boolean sameDayWithoutTime = fromKodi.toLocalTime().equals(LocalTime.MIDNIGHT)
                && fromKodi.toLocalDate().equals(local.toLocalDate());
        return !sameDayWithoutTime && fromKodi.isAfter(local);
    }

    private static Instant lastPlayedAt(KodiPlayback kodi, ZoneId zone) {
        return kodi.lastPlayed() == null ? null : kodi.lastPlayed().atZone(zone).toInstant();
    }

    /**
     * @param playCount    Anzahl der Wiedergaben oder {@code null}
     * @param lastPlayedAt letzte Wiedergabe oder {@code null}
     */
    record Playback(Integer playCount, Instant lastPlayedAt) {

        boolean isSameAs(Integer otherPlayCount, Instant otherLastPlayedAt) {
            return Objects.equals(playCount, otherPlayCount) && Objects.equals(lastPlayedAt, otherLastPlayedAt);
        }
    }
}
