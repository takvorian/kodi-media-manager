package de.tiktak.kmm.scanner.naming;

import de.tiktak.kmm.i18n.Localized;
import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.i18n.Messages;

/**
 * An invalid name pattern; the message names the problem and its position and is meant for the user interface.
 * Stays an {@link IllegalArgumentException}, so {@link #getMessage()} is the English text.
 */
public class NamePatternException extends IllegalArgumentException implements Localized {

    private final transient LocalizedText text;

    NamePatternException(LocalizedText text) {
        super(Messages.english(text));
        this.text = text;
    }

    @Override
    public LocalizedText localizedText() {
        return text;
    }
}
