package de.tiktak.kmm.movie;

import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.i18n.Localized;
import de.tiktak.kmm.i18n.LocalizedText;

/**
 * The translatable reason of a failure: the text of a {@link Localized} exception, otherwise its message as is.
 */
final class LocalizedReasons {

    private LocalizedReasons() {
    }

    static LocalizedText of(Throwable failure) {
        return failure instanceof Localized localized ? localized.localizedText()
                : LocalizedText.literal(failure.getMessage());
    }

    /** A hint of a plugin, which is a translatable text or plain text. */
    static LocalizedText hint(Object hint) {
        return hint instanceof LocalizedText text ? text : LocalizedText.literal(String.valueOf(hint));
    }

    /** Like {@link #of(Throwable)}, but falls back to the detail of the problem. */
    static LocalizedText of(ErrorResponseException problem) {
        return problem instanceof Localized localized ? localized.localizedText()
                : LocalizedText.literal(problem.getBody().getDetail());
    }
}
