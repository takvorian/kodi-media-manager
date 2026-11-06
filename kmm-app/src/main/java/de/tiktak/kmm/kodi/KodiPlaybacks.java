package de.tiktak.kmm.kodi;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

import de.tiktak.kmm.kodi.rpc.KodiPlayback;

/**
 * Compares the watched status and the user rating in KMM with those in Kodi and merges both. Kodi only adds: more
 * plays, a more recent last play or a rating. An "unwatched" or "not rated" in Kodi deletes nothing in KMM.
 */
final class KodiPlaybacks {

    private static final int NO_USER_RATING = 0;

    private KodiPlaybacks() {
    }

    /**
     * The states are also equal if KMM knows no play count and Kodi reports 0, and if Kodi only knows the date of the
     * same last play: Kodi does not take the time over from NFOs, and the more precise value in KMM must not get lost.
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
     * A rating from Kodi wins: rating happens in Kodi, and what KMM stores Kodi reads back from the NFO itself.
     *
     * @return the state in KMM, completed with the higher play count, the more recent last play and the rating from
     *         Kodi
     */
    static Playback merge(Playback current, KodiPlayback kodi, ZoneId zone) {
        int known = current.playCount() == null ? 0 : current.playCount();
        Integer mergedCount = kodi.playCount() > known ? Integer.valueOf(kodi.playCount()) : current.playCount();
        Instant mergedLastPlayed = isNewer(kodi, current.lastPlayedAt(), zone) ? lastPlayedAt(kodi, zone)
                : current.lastPlayedAt();
        Integer mergedRating = userRating(kodi) != null ? userRating(kodi) : current.userRating();
        return new Playback(mergedCount, mergedLastPlayed, mergedRating);
    }

    /** A date without time of the same day is not more recent, only less precise. */
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

    /** Kodi reports "not rated" as 0. */
    private static Integer userRating(KodiPlayback kodi) {
        return kodi.userRating() == NO_USER_RATING ? null : kodi.userRating();
    }

    /**
     * @param playCount    number of plays, or {@code null}
     * @param lastPlayedAt last play, or {@code null}
     * @param userRating   user rating 1–10, or {@code null}
     */
    record Playback(Integer playCount, Instant lastPlayedAt, Integer userRating) {
    }
}
