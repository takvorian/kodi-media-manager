package de.tiktak.kmm.migration.ember.settings;

/**
 * Embers Einstellungen für Serien.
 *
 * @param language      Sprache der Serien-Metadaten ({@code TVGeneralLanguage}), z. B. {@code de-DE}, oder {@code null}
 * @param imageLanguage erzwungene Sprache der Serienbilder (ISO 639-1) oder {@code null}, wenn Ember keine erzwingt
 * @param ordering      Episodenreihenfolge ({@code Standard}, {@code DVD}, {@code Absolute}, {@code DayOfYear})
 *                      oder {@code null}
 */
public record EmberTvSettings(String language, String imageLanguage, String ordering) {

    public static final EmberTvSettings NONE = new EmberTvSettings(null, null, null);
}
