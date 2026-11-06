package de.tiktak.kmm.movie;

import java.io.IOException;

import de.tiktak.kmm.i18n.Localized;
import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.i18n.Messages;

/**
 * A file system failure that KMM detects itself and describes for the user.
 */
class LocalizedIOException extends IOException implements Localized {

    private final transient LocalizedText text;

    LocalizedIOException(LocalizedText text) {
        super(Messages.english(text));
        this.text = text;
    }

    @Override
    public LocalizedText localizedText() {
        return text;
    }
}
