package de.tiktak.kmm.migration.ember.settings;

/**
 * Ember's settings for TV shows.
 *
 * @param language      language of the TV show metadata ({@code TVGeneralLanguage}), e.g. {@code de-DE}, or
 *                      {@code null}
 * @param imageLanguage forced language of the TV show images (ISO 639-1), or {@code null} if Ember forces none
 * @param ordering      episode order ({@code Standard}, {@code DVD}, {@code Absolute}, {@code DayOfYear}) or
 *                      {@code null}
 */
public record EmberTvSettings(String language, String imageLanguage, String ordering) {

    public static final EmberTvSettings NONE = new EmberTvSettings(null, null, null);
}
