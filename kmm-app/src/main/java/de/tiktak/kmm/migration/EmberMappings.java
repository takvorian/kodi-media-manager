package de.tiktak.kmm.migration;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import de.tiktak.kmm.migration.ember.library.EmberMovie.EmberRating;
import de.tiktak.kmm.settings.KmmSettings;

/**
 * Conversions that movies and TV shows from Ember need alike.
 */
final class EmberMappings {

    /** Ember stores the German certification as {@code Germany:16}, KMM like the TMDB scraper as {@code FSK 16}. */
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

    /** The language if KMM understands it; otherwise it counts as not given. */
    static String language(String emberLanguage) {
        return KmmSettings.isMetadataLanguage(emberLanguage) ? emberLanguage : null;
    }

    /** Ember creates entries with 0 and without votes for unknown ratings. */
    static boolean isRated(EmberRating rating) {
        return rating.value() > 0 || rating.votes() != null && rating.votes() > 0;
    }

    static Integer userRating(Integer userRating) {
        return userRating != null && userRating <= MAX_USER_RATING ? userRating : null;
    }

    /** Ember stores points in time as local time of the time zone it ran in. */
    static Instant toInstant(LocalDateTime localDateTime, ZoneId zone) {
        return localDateTime == null ? null : localDateTime.atZone(zone).toInstant();
    }
}
