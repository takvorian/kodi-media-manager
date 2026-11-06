package de.tiktak.kmm.plugin.api;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Texts for the user that many scrapers share, e.g. when their provider cannot be reached. The provider is the name
 * the user knows, such as {@code TMDB} or {@code fanart.tv}.
 */
public final class ScraperTexts {

    static final String PROVIDER = "provider";

    private ScraperTexts() {
    }

    /** The provider answers with an HTTP error. */
    public static LocalizedText httpError(String provider, int status) {
        return of("scraper.httpError", provider).with("status", status);
    }

    /**
     * The provider cannot be reached.
     *
     * @param reason message of the network error, shown as is
     */
    public static LocalizedText unreachable(String provider, String reason) {
        return of("scraper.unreachable", provider).with("reason", reason);
    }

    /** The request to the provider was interrupted, e.g. because KMM shuts down. */
    public static LocalizedText interrupted(String provider) {
        return of("scraper.interrupted", provider);
    }

    /** The provider sent something that cannot be read. */
    public static LocalizedText unexpectedResponse(String provider) {
        return of("scraper.unexpectedResponse", provider);
    }

    /** The provider does not accept the configured API key. */
    public static LocalizedText apiKeyRejected(String provider) {
        return of("scraper.apiKeyRejected", provider);
    }

    /** The provider allows no more requests for now. */
    public static LocalizedText rateLimitReached(String provider) {
        return of("scraper.rateLimitReached", provider);
    }

    /** No API key is configured for the provider. */
    public static LocalizedText apiKeyMissing(String provider) {
        return of("scraper.apiKeyMissing", provider);
    }

    private static LocalizedText of(String key, String provider) {
        return LocalizedText.of(key).with(PROVIDER, provider);
    }
}
