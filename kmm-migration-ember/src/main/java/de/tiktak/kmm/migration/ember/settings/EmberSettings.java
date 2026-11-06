package de.tiktak.kmm.migration.ember.settings;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The part of the Ember settings that KMM currently imports.
 *
 * @param version            version of the settings file (attribute {@code version})
 * @param language           user interface language, e.g. {@code de-DE}
 * @param videoExtensions    valid video file extensions
 * @param subtitleExtensions valid subtitle file extensions
 * @param proxy              proxy, if configured in Ember
 * @param movieLanguage      language of the movie metadata, e.g. {@code de-DE}, or {@code null}
 * @param movieImageLanguage forced language of the movie images (ISO 639-1), or {@code null} if Ember forces no
 *                           language
 * @param tv                 settings for TV shows
 * @param metaDataScan       whether Ember reads the media information when scraping (for movies or TV shows), or
 *                           {@code null} if the file does not say
 * @param rename             settings of the renamer from {@code AdvancedSettings.xml} or {@code null}
 * @param movieSet           settings for movie sets
 */
public record EmberSettings(
        int version,
        String language,
        List<String> videoExtensions,
        List<String> subtitleExtensions,
        Optional<EmberProxySettings> proxy,
        String movieLanguage,
        String movieImageLanguage,
        EmberTvSettings tv,
        Boolean metaDataScan,
        EmberRenameSettings rename,
        EmberMovieSetSettings movieSet) {

    public EmberSettings {
        videoExtensions = List.copyOf(videoExtensions);
        subtitleExtensions = List.copyOf(subtitleExtensions);
        Objects.requireNonNull(proxy, "proxy must not be null");
        tv = tv == null ? EmberTvSettings.NONE : tv;
        movieSet = movieSet == null ? EmberMovieSetSettings.NONE : movieSet;
    }

    /** Settings without those for movie sets. */
    public EmberSettings(int version, String language, List<String> videoExtensions, List<String> subtitleExtensions,
            Optional<EmberProxySettings> proxy, String movieLanguage, String movieImageLanguage, EmberTvSettings tv,
            Boolean metaDataScan, EmberRenameSettings rename) {
        this(version, language, videoExtensions, subtitleExtensions, proxy, movieLanguage, movieImageLanguage, tv,
                metaDataScan, rename, null);
    }

    /** Settings without details on TV shows. */
    public EmberSettings(int version, String language, List<String> videoExtensions, List<String> subtitleExtensions,
            Optional<EmberProxySettings> proxy, String movieLanguage, String movieImageLanguage) {
        this(version, language, videoExtensions, subtitleExtensions, proxy, movieLanguage, movieImageLanguage, null);
    }

    /** Settings without a value for reading the media information. */
    public EmberSettings(int version, String language, List<String> videoExtensions, List<String> subtitleExtensions,
            Optional<EmberProxySettings> proxy, String movieLanguage, String movieImageLanguage, EmberTvSettings tv) {
        this(version, language, videoExtensions, subtitleExtensions, proxy, movieLanguage, movieImageLanguage, tv,
                null);
    }

    /** Settings without those of the renamer. */
    public EmberSettings(int version, String language, List<String> videoExtensions, List<String> subtitleExtensions,
            Optional<EmberProxySettings> proxy, String movieLanguage, String movieImageLanguage, EmberTvSettings tv,
            Boolean metaDataScan) {
        this(version, language, videoExtensions, subtitleExtensions, proxy, movieLanguage, movieImageLanguage, tv,
                metaDataScan, null);
    }

    public EmberSettings withRename(EmberRenameSettings newRename) {
        return new EmberSettings(version, language, videoExtensions, subtitleExtensions, proxy, movieLanguage,
                movieImageLanguage, tv, metaDataScan, newRename, movieSet);
    }
}
