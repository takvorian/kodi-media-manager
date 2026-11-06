package de.tiktak.kmm.migration;

import java.util.List;

import de.tiktak.kmm.migration.ember.EmberLibraryStatistics;
import de.tiktak.kmm.migration.ember.EmberProfileInspection;
import de.tiktak.kmm.migration.ember.kodi.EmberKodiHost;
import de.tiktak.kmm.migration.ember.library.EmberMovieSource;
import de.tiktak.kmm.migration.ember.library.EmberTvShowSource;
import de.tiktak.kmm.migration.ember.settings.EmberProxySettings;
import de.tiktak.kmm.migration.ember.settings.EmberSettings;

/**
 * Answer of the profile analysis. Paths are returned as text, missing settings as {@code null}.
 *
 * @param sources   movie sources from Ember, e.g. as suggestion for path mappings
 * @param tvSources TV show sources from Ember
 * @param kodiHosts Kodi devices from Ember, without credentials
 */
public record EmberInspectionResponse(
        String profileDirectory,
        int schemaVersion,
        EmberLibraryStatistics library,
        List<Source> sources,
        List<Source> tvSources,
        List<KodiHost> kodiHosts,
        Settings settings) {

    static EmberInspectionResponse from(EmberProfileInspection inspection, List<EmberMovieSource> sources,
            List<EmberTvShowSource> tvSources, List<EmberKodiHost> kodiHosts) {
        return new EmberInspectionResponse(
                inspection.profile().directory().toString(),
                inspection.profile().schemaVersion(),
                inspection.library(),
                sources.stream().map(source -> new Source(source.name(), source.path())).toList(),
                tvSources.stream().map(source -> new Source(source.name(), source.path())).toList(),
                kodiHosts.stream().map(KodiHost::from).toList(),
                inspection.settings().map(Settings::from).orElse(null));
    }

    public record Source(String name, String path) {
    }

    /**
     * @param pathMappings number of path mappings
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
