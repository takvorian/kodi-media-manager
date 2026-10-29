package de.tiktak.kmm.plugin.api;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Vergleich von Titeln über Anbieter hinweg, etwa um sichere Treffer zu erkennen.
 */
public final class Titles {

    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^\\p{Alnum}]+");
    private static final Pattern LEADING_ARTICLE = Pattern.compile("^(the|der|die|das)\\s+");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    private Titles() {
    }

    /** Vergleichbare Form: ohne Groß-/Kleinschreibung, Akzente, Artikel am Anfang und Satzzeichen. */
    public static String normalize(String title) {
        if (title == null) {
            return "";
        }
        String plain = DIACRITICS.matcher(Normalizer.normalize(title, Normalizer.Form.NFD)).replaceAll("")
                .toLowerCase(Locale.ROOT).strip();
        return NOT_ALPHANUMERIC.matcher(LEADING_ARTICLE.matcher(plain).replaceFirst("")).replaceAll("");
    }

    /** Ob beide Titel in vergleichbarer Form gleich und nicht leer sind. */
    public static boolean same(String first, String second) {
        String normalized = normalize(first);
        return !normalized.isEmpty() && normalized.equals(normalize(second));
    }
}
