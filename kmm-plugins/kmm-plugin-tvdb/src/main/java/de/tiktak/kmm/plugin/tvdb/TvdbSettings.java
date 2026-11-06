package de.tiktak.kmm.plugin.tvdb;

import java.net.URI;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Optional;

import de.tiktak.kmm.plugin.api.PluginContext;
import de.tiktak.kmm.plugin.api.TvShowScraper;

/**
 * Settings of the plugin ({@code kmm.plugins.settings.tvdb.*} and the shared settings).
 *
 * @param apiKey               project key of the API v4; empty if not configured
 * @param pin                  PIN of a subscription or empty
 * @param language             language of the texts as ISO 639-2 code, as TVDB expects it, e.g. {@code deu}
 * @param seasonType           episode order at TVDB: {@code official}, {@code dvd} or {@code absolute}
 * @param certificationCountry country of the age rating as ISO 3166 alpha-3 code, e.g. {@code deu}
 * @param certificationPrefix  prefix of the age rating in the NFO, e.g. {@code FSK }
 * @param baseUrl              base URL of the API
 */
record TvdbSettings(
        String apiKey,
        String pin,
        String language,
        String seasonType,
        String certificationCountry,
        String certificationPrefix,
        URI baseUrl) {

    static final String API_KEY = "api-key";
    static final String PIN = "pin";
    static final String LANGUAGE = "language";
    static final String CERTIFICATION_COUNTRY = "certification-country";
    static final String CERTIFICATION_PREFIX = "certification-prefix";
    static final String BASE_URL = "base-url";

    static final String DEFAULT_LANGUAGE = "de-DE";
    static final String DEFAULT_CERTIFICATION_COUNTRY = "DE";
    static final String DEFAULT_CERTIFICATION_PREFIX = "FSK ";
    static final String DEFAULT_BASE_URL = "https://api4.thetvdb.com/v4";
    static final String OFFICIAL_ORDER = "official";
    private static final String FALLBACK_LANGUAGE = "eng";

    static TvdbSettings from(PluginContext context) {
        String language = setting(context, TvShowScraper.TV_LANGUAGE_SETTING)
                .or(() -> setting(context, LANGUAGE))
                .orElse(DEFAULT_LANGUAGE);
        return new TvdbSettings(
                setting(context, API_KEY).orElse(""),
                setting(context, PIN).orElse(""),
                iso3Language(language),
                seasonType(setting(context, TvShowScraper.EPISODE_ORDERING_SETTING).orElse("")),
                iso3Country(setting(context, CERTIFICATION_COUNTRY).orElse(DEFAULT_CERTIFICATION_COUNTRY)),
                context.setting(CERTIFICATION_PREFIX).orElse(DEFAULT_CERTIFICATION_PREFIX),
                URI.create(setting(context, BASE_URL).orElse(DEFAULT_BASE_URL)));
    }

    boolean hasApiKey() {
        return !apiKey.isEmpty();
    }

    /** {@code AIRED} corresponds to the official order at TVDB. */
    private static String seasonType(String ordering) {
        return switch (ordering) {
            case "DVD" -> "dvd";
            case "ABSOLUTE" -> "absolute";
            default -> OFFICIAL_ORDER;
        };
    }

    private static String iso3Language(String languageTag) {
        try {
            String iso3 = Locale.forLanguageTag(languageTag).getISO3Language();
            return iso3.isEmpty() ? FALLBACK_LANGUAGE : iso3;
        } catch (MissingResourceException e) {
            return FALLBACK_LANGUAGE;
        }
    }

    private static String iso3Country(String country) {
        try {
            return Locale.of("", country).getISO3Country().toLowerCase(Locale.ROOT);
        } catch (MissingResourceException e) {
            return country.toLowerCase(Locale.ROOT);
        }
    }

    private static Optional<String> setting(PluginContext context, String name) {
        return context.setting(name).map(String::strip).filter(text -> !text.isEmpty());
    }
}
