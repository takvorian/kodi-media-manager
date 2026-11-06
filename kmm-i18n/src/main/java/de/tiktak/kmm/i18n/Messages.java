package de.tiktak.kmm.i18n;

import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Renders {@link LocalizedText texts} on the server with the catalog {@code i18n/messages.properties}.
 *
 * <p>The base file is English; {@code messages_de.properties} only needs the texts the server shows itself in German
 * (exports). Every other key falls back to English. The user interface has its own translations of the same keys
 * under {@code server.*}.
 */
public final class Messages {

    public static final Locale FALLBACK = Locale.UK;
    /** Languages the catalog can be rendered in. */
    public static final List<Locale> SUPPORTED = List.of(Locale.UK, Locale.GERMAN);

    private static final String BUNDLE = "i18n.messages";
    /** Without this control the JVM default locale would sneak in as a fallback before the English base file. */
    private static final ResourceBundle.Control NO_DEFAULT_LOCALE =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
    private static final ConcurrentMap<String, MessageTemplate> TEMPLATES = new ConcurrentHashMap<>();

    private Messages() {
    }

    public static String english(LocalizedText text) {
        return render(text, FALLBACK);
    }

    /**
     * @return the text in the given language, in English if the language has no translation for the key, or the key
     *         itself if it is unknown
     */
    public static String render(LocalizedText text, Locale locale) {
        if (text.showsAsIs()) {
            Object literal = text.args().get(LocalizedText.LITERAL_ARGUMENT);
            return literal == null ? "" : literal.toString();
        }
        String pattern = pattern(text.key(), supported(locale));
        return TEMPLATES.computeIfAbsent(pattern, MessageTemplate::parse)
                .format(text.args(), nested -> render(nested, locale));
    }

    /** Whether the catalog defines the key; used by tests to find missing texts. */
    public static boolean contains(String key) {
        return bundle(FALLBACK).containsKey(key);
    }

    /** Maps any locale ({@code de_AT}, {@code en_GB}, {@code fr}) to a supported language. */
    public static Locale supported(Locale locale) {
        if (locale == null) {
            return FALLBACK;
        }
        return SUPPORTED.stream()
                .filter(candidate -> candidate.getLanguage().equals(locale.getLanguage()))
                .findFirst()
                .orElse(FALLBACK);
    }

    private static String pattern(String key, Locale locale) {
        try {
            return bundle(locale).getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    private static ResourceBundle bundle(Locale locale) {
        return ResourceBundle.getBundle(BUNDLE, locale, Messages.class.getClassLoader(), NO_DEFAULT_LOCALE);
    }
}
