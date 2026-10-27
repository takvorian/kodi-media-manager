package de.tiktak.kmm.nfo;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

/**
 * Sprachcodes nach ISO 639. Kodi und Ember verwenden die dreibuchstabigen Codes der
 * Terminologie-Variante ({@code deu}); Videodateien enthalten oft die bibliografische ({@code ger})
 * oder zweibuchstabige ({@code de}).
 */
public final class IsoLanguages {

    /** Bibliografische Codes, die von der Terminologie-Variante abweichen. */
    private static final Map<String, String> BIBLIOGRAPHIC = Map.ofEntries(
            Map.entry("alb", "sqi"), Map.entry("arm", "hye"), Map.entry("baq", "eus"), Map.entry("bur", "mya"),
            Map.entry("chi", "zho"), Map.entry("cze", "ces"), Map.entry("dut", "nld"), Map.entry("fre", "fra"),
            Map.entry("geo", "kat"), Map.entry("ger", "deu"), Map.entry("gre", "ell"), Map.entry("ice", "isl"),
            Map.entry("mac", "mkd"), Map.entry("mao", "mri"), Map.entry("may", "msa"), Map.entry("per", "fas"),
            Map.entry("rum", "ron"), Map.entry("slo", "slk"), Map.entry("tib", "bod"), Map.entry("wel", "cym"));
    private static final Map<String, Locale> BY_THREE_LETTERS = byThreeLetters();
    private static final int TWO_LETTERS = 2;
    private static final int THREE_LETTERS = 3;

    private IsoLanguages() {
    }

    /**
     * @return der dreibuchstabige Code der Terminologie-Variante oder {@code null}, wenn der Code
     *         keine bekannte Sprache bezeichnet (etwa {@code und})
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

    /** @return der englische Name der Sprache wie bei Ember ({@code German}) oder {@code null} */
    public static String englishName(String code) {
        String terminology = toTerminology(code);
        return terminology == null ? null : BY_THREE_LETTERS.get(terminology).getDisplayLanguage(Locale.ENGLISH);
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
