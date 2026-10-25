package de.tiktak.kmm.migration;

import java.util.List;

import de.tiktak.kmm.migration.ember.EmberLibraryStatistics;
import de.tiktak.kmm.migration.ember.EmberProfileInspection;
import de.tiktak.kmm.migration.ember.kodi.EmberKodiHost;
import de.tiktak.kmm.migration.ember.library.EmberMovieSource;
import de.tiktak.kmm.migration.ember.settings.EmberProxySettings;
import de.tiktak.kmm.migration.ember.settings.EmberSettings;

/**
 * Antwort der Profil-Analyse. Pfade werden als Text ausgegeben, fehlende Einstellungen als {@code null}.
 *
 * @param sources   Filmquellen aus Ember, z. B. als Vorschlag für Pfad-Zuordnungen
 * @param kodiHosts Kodi-Geräte aus Ember, ohne Zugangsdaten
 */
public record EmberInspectionResponse(
        String profileDirectory,
        int schemaVersion,
        EmberLibraryStatistics library,
        List<Source> sources,
        List<KodiHost> kodiHosts,
        Settings settings) {

    static EmberInspectionResponse from(EmberProfileInspection inspection, List<EmberMovieSource> sources,
            List<EmberKodiHost> kodiHosts) {
        return new EmberInspectionResponse(
                inspection.profile().directory().toString(),
                inspection.profile().schemaVersion(),
                inspection.library(),
                sources.stream().map(source -> new Source(source.name(), source.path())).toList(),
                kodiHosts.stream().map(KodiHost::from).toList(),
                inspection.settings().map(Settings::from).orElse(null));
    }

    public record Source(String name, String path) {
    }

    /**
     * @param pathMappings Anzahl der Pfad-Zuordnungen
     */
    public record KodiHost(String name, String address, int port, int pathMappings) {

        static KodiHost from(EmberKodiHost host) {
            return new KodiHost(host.label(), host.address(), host.port(), host.sources().size());
        }
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
