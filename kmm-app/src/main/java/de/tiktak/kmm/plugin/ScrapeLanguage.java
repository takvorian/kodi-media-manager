package de.tiktak.kmm.plugin;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Die Sprache eines einzelnen Vorgangs, wenn für den Eintrag oder seine Quelle eine eigene gilt. Plugins bekommen ihre
 * Sprache über den Kontext, den {@link PluginContextFactory} bei jeder Abfrage eines Scrapers neu erzeugt. Scraper
 * müssen deshalb innerhalb von {@link #with} abgefragt werden.
 */
public final class ScrapeLanguage {

    private static final ScopedValue<String> CURRENT = ScopedValue.newInstance();
    private static final String DEFAULT_KEY = "";
    private static final int ISO_639_1_LENGTH = 2;

    private ScrapeLanguage() {
    }

    /**
     * @param language Sprache wie {@code de-DE} oder {@code null}, dann gelten die allgemeinen Einstellungen
     */
    public static <T> T with(String language, Supplier<T> body) {
        return language == null ? body.get() : ScopedValue.where(CURRENT, language).call(body::get);
    }

    /** Die Sprache des laufenden Vorgangs oder {@code null}, wenn die allgemeinen Einstellungen gelten. */
    static String current() {
        return CURRENT.isBound() ? CURRENT.get() : null;
    }

    /** Unterscheidet zwischengespeicherte Antworten nach der Sprache, in der sie geholt wurden. */
    public static String cacheKey() {
        return CURRENT.isBound() ? CURRENT.get() : DEFAULT_KEY;
    }

    /** Die zweistellige Sprache des laufenden Vorgangs, sonst die übergebene. */
    public static String preferredImageLanguage(String fallback) {
        return CURRENT.isBound() ? CURRENT.get().substring(0, ISO_639_1_LENGTH).toLowerCase(Locale.ROOT) : fallback;
    }
}
