package de.tiktak.kmm.plugin.api;

import de.tiktak.kmm.i18n.LocalizedException;
import de.tiktak.kmm.i18n.LocalizedText;

/**
 * The provider of a scraper cannot be reached or rejects the request. The message is shown to the user and should
 * name the cause in plain words.
 */
public class ScraperException extends LocalizedException {

    public ScraperException(LocalizedText message) {
        super(message);
    }

    public ScraperException(LocalizedText message, Throwable cause) {
        super(message, cause);
    }

    /** For messages that are not translated, e.g. the text of an external service. */
    public ScraperException(String message) {
        this(LocalizedText.literal(message));
    }

    /** For messages that are not translated, e.g. the text of an external service. */
    public ScraperException(String message, Throwable cause) {
        this(LocalizedText.literal(message), cause);
    }
}
