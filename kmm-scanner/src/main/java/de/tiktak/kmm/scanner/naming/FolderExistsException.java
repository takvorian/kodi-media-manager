package de.tiktak.kmm.scanner.naming;

import java.io.IOException;
import java.nio.file.Path;

import de.tiktak.kmm.i18n.Localized;
import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.i18n.Messages;

/**
 * A folder cannot be moved because the target folder already exists. {@link #getMessage()} is the English text.
 */
public class FolderExistsException extends IOException implements Localized {

    private final transient LocalizedText text;

    FolderExistsException(Path target) {
        this(LocalizedText.of("scanner.folderExists").with("folder", target));
    }

    private FolderExistsException(LocalizedText text) {
        super(Messages.english(text));
        this.text = text;
    }

    @Override
    public LocalizedText localizedText() {
        return text;
    }
}
