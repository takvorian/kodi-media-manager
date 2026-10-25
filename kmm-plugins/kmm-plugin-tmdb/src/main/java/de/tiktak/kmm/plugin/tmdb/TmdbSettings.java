package de.tiktak.kmm.plugin.tmdb;

import java.net.URI;
import java.util.Optional;

import de.tiktak.kmm.plugin.api.PluginContext;

/**
 * Einstellungen des Plugins ({@code kmm.plugins.settings.tmdb.*}).
 *
 * @param apiKey               TMDB-API-Key (v3) oder Lese-Token (v4); leer, wenn nicht konfiguriert
 * @param language             Sprache der Texte, z. B. {@code de-DE}
 * @param certificationCountry Land, dessen Altersfreigabe übernommen wird, z. B. {@code DE}
 * @param certificationPrefix  Präfix der Altersfreigabe in der NFO, z. B. {@code FSK }
 * @param baseUrl              Basis-URL der TMDB-API
 * @param imageBaseUrl         Basis-URL für Vorschaubilder
 */
record TmdbSettings(
        String apiKey,
        String language,
        String certificationCountry,
        String certificationPrefix,
        URI baseUrl,
        URI imageBaseUrl) {

    static final String API_KEY = "api-key";
    static final String LANGUAGE = "language";
    static final String CERTIFICATION_COUNTRY = "certification-country";
    static final String CERTIFICATION_PREFIX = "certification-prefix";
    static final String BASE_URL = "base-url";
    static final String IMAGE_BASE_URL = "image-base-url";

    static final String DEFAULT_LANGUAGE = "de-DE";
    static final String DEFAULT_CERTIFICATION_COUNTRY = "DE";
    static final String DEFAULT_CERTIFICATION_PREFIX = "FSK ";
    static final String DEFAULT_BASE_URL = "https://api.themoviedb.org/3";
    static final String DEFAULT_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w185";

    static TmdbSettings from(PluginContext context) {
        return new TmdbSettings(
                context.setting(API_KEY).map(String::strip).orElse(""),
                setting(context, LANGUAGE, DEFAULT_LANGUAGE),
                setting(context, CERTIFICATION_COUNTRY, DEFAULT_CERTIFICATION_COUNTRY),
                context.setting(CERTIFICATION_PREFIX).orElse(DEFAULT_CERTIFICATION_PREFIX),
                URI.create(setting(context, BASE_URL, DEFAULT_BASE_URL)),
                URI.create(setting(context, IMAGE_BASE_URL, DEFAULT_IMAGE_BASE_URL)));
    }

    boolean hasApiKey() {
        return !apiKey.isEmpty();
    }

    private static String setting(PluginContext context, String name, String defaultValue) {
        Optional<String> value = context.setting(name).map(String::strip).filter(text -> !text.isEmpty());
        return value.orElse(defaultValue);
    }
}
