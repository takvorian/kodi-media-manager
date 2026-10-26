package de.tiktak.kmm.migration;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import de.tiktak.kmm.migration.ember.library.EmberMovie.EmberRating;

/**
 * Umrechnungen, die Filme und Serien aus Ember gleichermaßen brauchen.
 */
final class EmberMappings {

    /** Ember speichert die deutsche Freigabe als {@code Germany:16}, KMM wie der TMDB-Scraper als {@code FSK 16}. */
    private static final String EMBER_GERMAN_CERTIFICATION = "Germany:";
    private static final String KMM_GERMAN_CERTIFICATION = "FSK ";
    private static final int MAX_USER_RATING = 10;

    private EmberMappings() {
    }

    static String certification(String mpaa) {
        if (mpaa != null && mpaa.startsWith(EMBER_GERMAN_CERTIFICATION)) {
            return KMM_GERMAN_CERTIFICATION + mpaa.substring(EMBER_GERMAN_CERTIFICATION.length());
        }
        return mpaa;
    }

    /** Ember legt für unbekannte Bewertungen Einträge mit 0 und ohne Stimmen an. */
    static boolean isRated(EmberRating rating) {
        return rating.value() > 0 || rating.votes() != null && rating.votes() > 0;
    }

    static Integer userRating(Integer userRating) {
        return userRating != null && userRating <= MAX_USER_RATING ? userRating : null;
    }

    /** Ember speichert Zeitpunkte als lokale Uhrzeit der Zeitzone, in der es lief. */
    static Instant toInstant(LocalDateTime localDateTime, ZoneId zone) {
        return localDateTime == null ? null : localDateTime.atZone(zone).toInstant();
    }
}
