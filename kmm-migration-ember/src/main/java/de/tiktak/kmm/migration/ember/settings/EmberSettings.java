package de.tiktak.kmm.migration.ember.settings;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Der Teil der Ember-Einstellungen, den KMM derzeit übernimmt.
 *
 * @param version            Version der Settings-Datei (Attribut {@code version})
 * @param language           Oberflächensprache, z. B. {@code de-DE}
 * @param videoExtensions    gültige Video-Dateiendungen
 * @param subtitleExtensions gültige Untertitel-Dateiendungen
 * @param proxy              Proxy, sofern in Ember konfiguriert
 * @param movieLanguage      Sprache der Film-Metadaten, z. B. {@code de-DE}, oder {@code null}
 * @param movieImageLanguage erzwungene Sprache der Filmbilder (ISO 639-1) oder {@code null}, wenn
 *                           Ember keine Sprache erzwingt
 * @param tv                 Einstellungen für Serien
 */
public record EmberSettings(
        int version,
        String language,
        List<String> videoExtensions,
        List<String> subtitleExtensions,
        Optional<EmberProxySettings> proxy,
        String movieLanguage,
        String movieImageLanguage,
        EmberTvSettings tv) {

    public EmberSettings {
        videoExtensions = List.copyOf(videoExtensions);
        subtitleExtensions = List.copyOf(subtitleExtensions);
        Objects.requireNonNull(proxy, "proxy must not be null");
        tv = tv == null ? EmberTvSettings.NONE : tv;
    }

    /** Einstellungen ohne Angaben zu Serien. */
    public EmberSettings(int version, String language, List<String> videoExtensions, List<String> subtitleExtensions,
            Optional<EmberProxySettings> proxy, String movieLanguage, String movieImageLanguage) {
        this(version, language, videoExtensions, subtitleExtensions, proxy, movieLanguage, movieImageLanguage, null);
    }
}
