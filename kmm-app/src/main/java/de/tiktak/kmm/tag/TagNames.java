package de.tiktak.kmm.tag;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.web.LocalizedProblemException;

/**
 * Checks tag names: trimmed, not empty and at most as long as the column.
 */
final class TagNames {

    static final int MAX_LENGTH = 255;

    private TagNames() {
    }

    /**
     * @throws InvalidTagException (422) if the name is empty or too long
     */
    static String clean(String name) {
        String cleaned = name == null ? "" : name.strip();
        if (cleaned.isEmpty() || cleaned.length() > MAX_LENGTH) {
            throw new InvalidTagException();
        }
        return cleaned;
    }

    /** A tag name is empty or too long. Answered as HTTP 422. */
    static final class InvalidTagException extends LocalizedProblemException {

        InvalidTagException() {
            super(HttpStatus.UNPROCESSABLE_CONTENT, LocalizedText.of("tag.invalid.title"),
                    LocalizedText.of("tag.invalid.detail").with("max", MAX_LENGTH), null);
        }
    }
}
