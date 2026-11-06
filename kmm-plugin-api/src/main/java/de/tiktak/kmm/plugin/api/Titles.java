package de.tiktak.kmm.plugin.api;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Compares titles across providers, e.g. to recognize safe matches.
 */
public final class Titles {

    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^\\p{Alnum}]+");
    private static final Pattern LEADING_ARTICLE = Pattern.compile("^(the|der|die|das)\\s+");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    private Titles() {
    }

    /** Comparable form: without case, accents, leading articles and punctuation. */
    public static String normalize(String title) {
        if (title == null) {
            return "";
        }
        String plain = DIACRITICS.matcher(Normalizer.normalize(title, Normalizer.Form.NFD)).replaceAll("")
                .toLowerCase(Locale.ROOT).strip();
        return NOT_ALPHANUMERIC.matcher(LEADING_ARTICLE.matcher(plain).replaceFirst("")).replaceAll("");
    }

    /** Whether both titles are equal and not empty in comparable form. */
    public static boolean same(String first, String second) {
        String normalized = normalize(first);
        return !normalized.isEmpty() && normalized.equals(normalize(second));
    }
}
