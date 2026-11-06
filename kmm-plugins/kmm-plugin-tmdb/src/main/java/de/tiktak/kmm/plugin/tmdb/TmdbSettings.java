package de.tiktak.kmm.plugin.tmdb;

import java.net.URI;
import java.util.Optional;

import de.tiktak.kmm.plugin.api.PluginContext;
import de.tiktak.kmm.plugin.api.TvShowScraper;

/**
 * Settings of the plugin ({@code kmm.plugins.settings.tmdb.*}).
 *
 * @param apiKey               TMDB API key (v3) or read access token (v4); empty if not configured
 * @param language             language of the texts, e.g. {@code de-DE}
 * @param tvLanguage           language of the TV show texts; like {@code language} without a setting of its own
 * @param certificationCountry country whose age rating is used, e.g. {@code DE}
 * @param certificationPrefix  prefix of the age rating in the NFO, e.g. {@code FSK }
 * @param baseUrl              base URL of the TMDB API
 * @param imageHost            base URL of the TMDB images; the size (e.g. {@code w185}) is appended
 */
record TmdbSettings(
        String apiKey,
        String language,
        String tvLanguage,
        String certificationCountry,
        String certificationPrefix,
        URI baseUrl,
        URI imageHost) {

    static final String API_KEY = "api-key";
    static final String LANGUAGE = "language";
    static final String CERTIFICATION_COUNTRY = "certification-country";
    static final String CERTIFICATION_PREFIX = "certification-prefix";
    static final String BASE_URL = "base-url";
    static final String IMAGE_HOST = "image-host";

    static final String DEFAULT_LANGUAGE = "de-DE";
    static final String DEFAULT_CERTIFICATION_COUNTRY = "DE";
    static final String DEFAULT_CERTIFICATION_PREFIX = "FSK ";
    static final String DEFAULT_BASE_URL = "https://api.themoviedb.org/3";
    static final String DEFAULT_IMAGE_HOST = "https://image.tmdb.org/t/p";
    private static final char LANGUAGE_REGION_SEPARATOR = '-';

    static TmdbSettings from(PluginContext context) {
        String language = setting(context, LANGUAGE, DEFAULT_LANGUAGE);
        return new TmdbSettings(
                context.setting(API_KEY).map(String::strip).orElse(""),
                language,
                setting(context, TvShowScraper.TV_LANGUAGE_SETTING, language),
                setting(context, CERTIFICATION_COUNTRY, DEFAULT_CERTIFICATION_COUNTRY),
                context.setting(CERTIFICATION_PREFIX).orElse(DEFAULT_CERTIFICATION_PREFIX),
                URI.create(setting(context, BASE_URL, DEFAULT_BASE_URL)),
                URI.create(setting(context, IMAGE_HOST, DEFAULT_IMAGE_HOST)));
    }

    boolean hasApiKey() {
        return !apiKey.isEmpty();
    }

    /** Language without region for images, e.g. {@code de} from {@code de-DE}. */
    String imageLanguage() {
        int separator = language.indexOf(LANGUAGE_REGION_SEPARATOR);
        return separator > 0 ? language.substring(0, separator) : language;
    }

    /** URL of an image in a TMDB size, e.g. {@code w185} or {@code original}. */
    URI imageUrl(String size, String filePath) {
        return URI.create(imageHost.toString().replaceAll("/+$", "") + "/" + size + filePath);
    }

    private static String setting(PluginContext context, String name, String defaultValue) {
        Optional<String> value = context.setting(name).map(String::strip).filter(text -> !text.isEmpty());
        return value.orElse(defaultValue);
    }
}
