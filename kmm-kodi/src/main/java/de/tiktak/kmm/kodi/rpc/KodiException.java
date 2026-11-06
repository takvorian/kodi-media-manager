package de.tiktak.kmm.kodi.rpc;

import de.tiktak.kmm.i18n.LocalizedException;
import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Kodi cannot be reached, rejects the login or reports an error. The message is meant for the user interface.
 */
public class KodiException extends LocalizedException {

    KodiException(LocalizedText message) {
        super(message);
    }

    KodiException(LocalizedText message, Throwable cause) {
        super(message, cause);
    }
}
