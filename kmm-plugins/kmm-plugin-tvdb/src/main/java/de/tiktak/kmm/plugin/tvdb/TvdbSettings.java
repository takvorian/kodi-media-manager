package de.tiktak.kmm.plugin.tvdb;

import java.net.URI;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Optional;

import de.tiktak.kmm.plugin.api.PluginContext;
import de.tiktak.kmm.plugin.api.TvShowScraper;

/**
 * Einstellungen des Plugins ({@code kmm.plugins.settings.tvdb.*} und die gemeinsamen Einstellungen).
 *
 * @param apiKey               Projekt-Key der API v4; leer, wenn nicht konfiguriert
 * @param pin                  PIN eines Abonnements oder leer
 * @param language             Sprache der Texte als ISO-639-2-Code, wie TVDB sie erwartet, z. B. {@code deu}
 * @param seasonType           Episodenreihenfolge bei TVDB: {@code official}, {@code dvd} oder {@code absolute}
 * @param certificationCountry Land der Altersfreigabe als ISO-3166-alpha-3-Code, z. B. {@code deu}
 * @param certificationPrefix  Präfix der Altersfreigabe in der NFO, z. B. {@code FSK }
 * @param baseUrl              Basis-URL der API
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

    /** {@code AIRED} entspricht bei TVDB der offiziellen Reihenfolge. */
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
