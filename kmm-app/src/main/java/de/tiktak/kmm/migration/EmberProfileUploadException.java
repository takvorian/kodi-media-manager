package de.tiktak.kmm.migration;

import de.tiktak.kmm.i18n.LocalizedException;
import de.tiktak.kmm.i18n.LocalizedText;

/**
 * The uploaded Ember profile is missing, incomplete or cannot be replaced right now.
 */
class EmberProfileUploadException extends LocalizedException {

    private static final long serialVersionUID = 1L;

    EmberProfileUploadException(LocalizedText message) {
        super(message);
    }
}
