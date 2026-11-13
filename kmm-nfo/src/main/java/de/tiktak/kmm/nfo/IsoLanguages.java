package de.tiktak.kmm.nfo;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;

/**
 * Language codes according to ISO 639. Kodi and Ember use the three-letter codes of the terminology variant
 * ({@code deu}); video files often contain the bibliographic one ({@code ger}) or the two-letter one ({@code de}).
 */
public final class IsoLanguages {

    /** Bibliographic codes that differ from the terminology variant. */
    private static final Map<String, String> BIBLIOGRAPHIC = Map.ofEntries(
            Map.entry("alb", "sqi"), Map.entry("arm", "hye"), Map.entry("baq", "eus"), Map.entry("bur", "mya"),
            Map.entry("chi", "zho"), Map.entry("cze", "ces"), Map.entry("dut", "nld"), Map.entry("fre", "fra"),
            Map.entry("geo", "kat"), Map.entry("ger", "deu"), Map.entry("gre", "ell"), Map.entry("ice", "isl"),
            Map.entry("mac", "mkd"), Map.entry("mao", "mri"), Map.entry("may", "msa"), Map.entry("per", "fas"),
            Map.entry("rum", "ron"), Map.entry("slo", "slk"), Map.entry("tib", "bod"), Map.entry("wel", "cym"));
    private static final Map<String, Locale> BY_THREE_LETTERS = byThreeLetters();
    /** Special codes of ISO 639-2 that denote no particular language. */
    private static final Set<String> NO_LANGUAGE = Set.of("und", "mul", "mis", "zxx");
    private static final int TWO_LETTERS = 2;
    private static final int THREE_LETTERS = 3;

    private IsoLanguages() {
    }

    /**
     * @return the three-letter code of the terminology variant, or {@code null} if the code does not denote a known
     *         language (such as {@code und})
     */
    public static String toTerminology(String code) {
        if (code == null) {
            return null;
        }
        String lower = code.trim().toLowerCase(Locale.ROOT);
        String terminology = switch (lower.length()) {
            case TWO_LETTERS -> threeLetters(Locale.of(lower));
            case THREE_LETTERS -> BIBLIOGRAPHIC.getOrDefault(lower, lower);
            default -> null;
        };
        return terminology != null && BY_THREE_LETTERS.containsKey(terminology) ? terminology : null;
    }

    /**
     * Like {@link #toTerminology(String)}, but also accepts languages that only have a three-letter code, such as
     * {@code gsw} (Swiss German). Only for language tags of streams: in file names such codes would mistake words
     * like {@code sdh} (Southern Kurdish) for a language.
     *
     * @return the three-letter code of the terminology variant, or {@code null} if the code does not denote a known
     *         language
     */
    public static String streamLanguage(String code) {
        String terminology = toTerminology(code);
        if (terminology != null || code == null) {
            return terminology;
        }
        String lower = code.trim().toLowerCase(Locale.ROOT);
        return lower.length() == THREE_LETTERS && !NO_LANGUAGE.contains(lower) && hasName(lower) ? lower : null;
    }

    /** @return the English name of the language as in Ember ({@code German}) or {@code null} */
    public static String englishName(String code) {
        String language = streamLanguage(code);
        if (language == null) {
            return null;
        }
        return BY_THREE_LETTERS.getOrDefault(language, Locale.of(language)).getDisplayLanguage(Locale.ENGLISH);
    }

    /** Java knows the name of a language only if the code is a real one. */
    private static boolean hasName(String code) {
        return !Locale.of(code).getDisplayLanguage(Locale.ENGLISH).equalsIgnoreCase(code);
    }

    private static Map<String, Locale> byThreeLetters() {
        Map<String, Locale> locales = new HashMap<>();
        for (String language : Locale.getISOLanguages()) {
            Locale locale = Locale.of(language);
            String threeLetters = threeLetters(locale);
            if (threeLetters != null) {
                locales.put(threeLetters, locale);
            }
        }
        return Map.copyOf(locales);
    }

    private static String threeLetters(Locale locale) {
        try {
            String code = locale.getISO3Language();
            return code.length() == THREE_LETTERS ? code : null;
        } catch (MissingResourceException e) {
            return null;
        }
    }
}
