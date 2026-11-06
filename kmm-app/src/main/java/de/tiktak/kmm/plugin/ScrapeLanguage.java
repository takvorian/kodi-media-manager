package de.tiktak.kmm.plugin;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * The language of a single operation, if the entry or its source has its own. Plugins receive their
 * language via the context that {@link PluginContextFactory} creates anew on every lookup of a scraper. Scrapers
 * must therefore be looked up within {@link #with}.
 */
public final class ScrapeLanguage {

    private static final ScopedValue<String> CURRENT = ScopedValue.newInstance();
    private static final String DEFAULT_KEY = "";
    private static final int ISO_639_1_LENGTH = 2;

    private ScrapeLanguage() {
    }

    /**
     * @param language language such as {@code de-DE} or {@code null}, in which case the general settings apply
     */
    public static <T> T with(String language, Supplier<T> body) {
        return language == null ? body.get() : ScopedValue.where(CURRENT, language).call(body::get);
    }

    /** The language of the current operation or {@code null} if the general settings apply. */
    static String current() {
        return CURRENT.isBound() ? CURRENT.get() : null;
    }

    /** Distinguishes cached responses by the language in which they were fetched. */
    public static String cacheKey() {
        return CURRENT.isBound() ? CURRENT.get() : DEFAULT_KEY;
    }

    /** The two-letter language of the current operation, otherwise the one passed in. */
    public static String preferredImageLanguage(String fallback) {
        return CURRENT.isBound() ? CURRENT.get().substring(0, ISO_639_1_LENGTH).toLowerCase(Locale.ROOT) : fallback;
    }
}
