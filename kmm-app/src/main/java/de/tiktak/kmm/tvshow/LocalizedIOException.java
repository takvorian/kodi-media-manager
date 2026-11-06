package de.tiktak.kmm.tvshow;

import java.io.IOException;

import de.tiktak.kmm.i18n.Localized;
import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.i18n.Messages;

/**
 * A file operation failed for a reason the user should read in their language.
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
