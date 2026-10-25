package de.tiktak.kmm.migration;

import java.util.List;

import de.tiktak.kmm.migration.ember.EmberLibraryStatistics;
import de.tiktak.kmm.migration.ember.EmberProfileInspection;
import de.tiktak.kmm.migration.ember.library.EmberMovieSource;
import de.tiktak.kmm.migration.ember.settings.EmberProxySettings;
import de.tiktak.kmm.migration.ember.settings.EmberSettings;

/**
 * Antwort der Profil-Analyse. Pfade werden als Text ausgegeben, fehlende Einstellungen als {@code null}.
 *
 * @param sources Filmquellen aus Ember, z. B. als Vorschlag für Pfad-Zuordnungen
 */
public record EmberInspectionResponse(
        String profileDirectory,
        int schemaVersion,
        EmberLibraryStatistics library,
        List<Source> sources,
        Settings settings) {

    static EmberInspectionResponse from(EmberProfileInspection inspection, List<EmberMovieSource> sources) {
        return new EmberInspectionResponse(
                inspection.profile().directory().toString(),
                inspection.profile().schemaVersion(),
                inspection.library(),
                sources.stream().map(source -> new Source(source.name(), source.path())).toList(),
                inspection.settings().map(Settings::from).orElse(null));
    }

    public record Source(String name, String path) {
    }

    public record Settings(
            String language,
            List<String> videoExtensions,
            List<String> subtitleExtensions,
            EmberProxySettings proxy,
            String movieLanguage,
            String movieImageLanguage) {

        static Settings from(EmberSettings settings) {
            return new Settings(
                    settings.language(),
                    settings.videoExtensions(),
                    settings.subtitleExtensions(),
                    settings.proxy().orElse(null),
                    settings.movieLanguage(),
                    settings.movieImageLanguage());
        }
    }
}
