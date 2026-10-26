package de.tiktak.kmm.plugin.tmdb;

import java.net.URI;
import java.util.Optional;

import de.tiktak.kmm.plugin.api.PluginContext;
import de.tiktak.kmm.plugin.api.TvShowScraper;

/**
 * Einstellungen des Plugins ({@code kmm.plugins.settings.tmdb.*}).
 *
 * @param apiKey               TMDB-API-Key (v3) oder Lese-Token (v4); leer, wenn nicht konfiguriert
 * @param language             Sprache der Texte, z. B. {@code de-DE}
 * @param tvLanguage           Sprache der Serientexte; ohne eigene Einstellung wie {@code language}
 * @param certificationCountry Land, dessen Altersfreigabe übernommen wird, z. B. {@code DE}
 * @param certificationPrefix  Präfix der Altersfreigabe in der NFO, z. B. {@code FSK }
 * @param baseUrl              Basis-URL der TMDB-API
 * @param imageHost            Basis-URL der TMDB-Bilder; die Größe (z. B. {@code w185}) wird angehängt
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

    /** Sprache ohne Region für Bilder, z. B. {@code de} aus {@code de-DE}. */
    String imageLanguage() {
        int separator = language.indexOf(LANGUAGE_REGION_SEPARATOR);
        return separator > 0 ? language.substring(0, separator) : language;
    }

    /** URL eines Bildes in einer TMDB-Größe, z. B. {@code w185} oder {@code original}. */
    URI imageUrl(String size, String filePath) {
        return URI.create(imageHost.toString().replaceAll("/+$", "") + "/" + size + filePath);
    }

    private static String setting(PluginContext context, String name, String defaultValue) {
        Optional<String> value = context.setting(name).map(String::strip).filter(text -> !text.isEmpty());
        return value.orElse(defaultValue);
    }
}
