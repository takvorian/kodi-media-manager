package de.tiktak.kmm.web;

import org.springframework.web.ErrorResponse;

import de.tiktak.kmm.i18n.Localized;
import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Turns causes and messages of other modules into translatable texts for result lists and problem details.
 */
public final class LocalizedTexts {

    private LocalizedTexts() {
    }

    /**
     * The message of an exception: its translatable text if it has one, otherwise the detail of a problem or the
     * exception message, shown as is.
     */
    public static LocalizedText of(Throwable cause) {
        if (cause instanceof Localized localized) {
            return localized.localizedText();
        }
        if (cause instanceof ErrorResponse response && response.getBody().getDetail() != null) {
            return LocalizedText.literal(response.getBody().getDetail());
        }
        return LocalizedText.literal(String.valueOf(cause.getMessage()));
    }

    /**
     * A message that is either a translatable text already or a plain string, shown as is. Bridges APIs of other
     * modules that still deliver plain strings.
     */
    public static LocalizedText asText(Object message) {
        return message instanceof LocalizedText text ? text : LocalizedText.literal(String.valueOf(message));
    }
}
