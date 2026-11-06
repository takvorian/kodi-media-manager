package de.tiktak.kmm.i18n;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A text for the user as a message key plus named arguments. The user interface translates it into the active
 * language; {@link Messages} renders it on the server (English fallback, exports).
 *
 * <p>Arguments are numbers, booleans, nested texts or strings. Other values are stored as their string form so the
 * JSON representation stays simple (a {@code Path} would otherwise become a {@code file://} URI).
 *
 * @param key  message key in {@code i18n/messages.properties}, e.g. {@code artwork.imageMissing}
 * @param args named arguments for the placeholders of the message
 */
public record LocalizedText(String key, Map<String, Object> args) {

    /** Key of a text that is shown as is, such as a file name or a message from an external service. */
    public static final String LITERAL_KEY = "literal";
    /** Argument holding the text of a {@link #literal(String) literal}. */
    public static final String LITERAL_ARGUMENT = "text";

    public LocalizedText {
        Objects.requireNonNull(key, "key must not be null");
        args = Collections.unmodifiableMap(new LinkedHashMap<>(args));
    }

    public static LocalizedText of(String key) {
        return new LocalizedText(key, Map.of());
    }

    /** A text that is not translated, e.g. a file name or the message of an external service. */
    public static LocalizedText literal(String text) {
        return of(LITERAL_KEY).with(LITERAL_ARGUMENT, text);
    }

    /** Returns a copy with one more argument. */
    public LocalizedText with(String name, Object value) {
        Objects.requireNonNull(name, "name must not be null");
        Map<String, Object> extended = new LinkedHashMap<>(args);
        extended.put(name, normalize(value));
        return new LocalizedText(key, extended);
    }

    public boolean showsAsIs() {
        return LITERAL_KEY.equals(key);
    }

    private static Object normalize(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean || value instanceof LocalizedText) {
            return value;
        }
        return value.toString();
    }
}
