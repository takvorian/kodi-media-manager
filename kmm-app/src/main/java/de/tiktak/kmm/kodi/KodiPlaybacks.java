package de.tiktak.kmm.kodi;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

import de.tiktak.kmm.kodi.rpc.KodiPlayback;

/**
 * Vergleicht den Gesehen-Status und die eigene Bewertung in KMM mit denen in Kodi und führt beide zusammen.
 * Kodi ergänzt nur: mehr Wiedergaben, eine neuere letzte Wiedergabe oder eine Bewertung. Ein „ungesehen“
 * oder „nicht bewertet“ in Kodi löscht nichts in KMM.
 */
final class KodiPlaybacks {

    private static final int NO_USER_RATING = 0;

    private KodiPlaybacks() {
    }

    /**
     * Gleich sind die Stände auch, wenn KMM keine Wiedergabezahl kennt und Kodi 0 meldet, und wenn Kodi
     * zur selben letzten Wiedergabe nur das Datum kennt: Aus NFOs übernimmt Kodi die Uhrzeit nicht, die
     * genauere Angabe in KMM soll dabei nicht verloren gehen.
     */
    static boolean isCurrent(Playback current, KodiPlayback kodi, ZoneId zone) {
        int known = current.playCount() == null ? 0 : current.playCount();
        if (known != kodi.playCount() || !Objects.equals(current.userRating(), userRating(kodi))) {
            return false;
        }
        LocalDateTime local = current.lastPlayedAt() == null ? null
                : LocalDateTime.ofInstant(current.lastPlayedAt(), zone);
        LocalDateTime fromKodi = kodi.lastPlayed();
        if (Objects.equals(local, fromKodi)) {
            return true;
        }
        return local != null && fromKodi != null && fromKodi.toLocalTime().equals(LocalTime.MIDNIGHT)
                && fromKodi.toLocalDate().equals(local.toLocalDate());
    }

    /**
     * Eine Bewertung aus Kodi gewinnt: Bewertet wird in Kodi, und was KMM speichert, liest Kodi danach
     * selbst wieder aus der NFO.
     *
     * @return der Stand in KMM, ergänzt um die höhere Wiedergabezahl, die neuere letzte Wiedergabe und die
     *         Bewertung aus Kodi
     */
    static Playback merge(Playback current, KodiPlayback kodi, ZoneId zone) {
        int known = current.playCount() == null ? 0 : current.playCount();
        Integer mergedCount = kodi.playCount() > known ? Integer.valueOf(kodi.playCount()) : current.playCount();
        Instant mergedLastPlayed = isNewer(kodi, current.lastPlayedAt(), zone) ? lastPlayedAt(kodi, zone)
                : current.lastPlayedAt();
        Integer mergedRating = userRating(kodi) != null ? userRating(kodi) : current.userRating();
        return new Playback(mergedCount, mergedLastPlayed, mergedRating);
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

    /** Kodi meldet „nicht bewertet“ als 0. */
    private static Integer userRating(KodiPlayback kodi) {
        return kodi.userRating() == NO_USER_RATING ? null : kodi.userRating();
    }

    /**
     * @param playCount    Anzahl der Wiedergaben oder {@code null}
     * @param lastPlayedAt letzte Wiedergabe oder {@code null}
     * @param userRating   eigene Bewertung 1–10 oder {@code null}
     */
    record Playback(Integer playCount, Instant lastPlayedAt, Integer userRating) {
    }
}
