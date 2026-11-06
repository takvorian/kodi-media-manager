package de.tiktak.kmm.nfo;

import java.nio.file.Path;

import de.tiktak.kmm.i18n.LocalizedException;
import de.tiktak.kmm.i18n.LocalizedText;

/**
 * An NFO file cannot be read or written.
 */
public class NfoException extends LocalizedException {

    private NfoException(LocalizedText message, Throwable cause) {
        super(message, cause);
    }

    static NfoException unreadable(Path nfoFile, Throwable cause) {
        return new NfoException(LocalizedText.of("nfo.unreadable").with("file", nfoFile), cause);
    }

    static NfoException missingEntry(Path nfoFile) {
        return new NfoException(LocalizedText.of("nfo.missingEntry").with("file", nfoFile), null);
    }

    static NfoException unwritable(Path nfoFile, Throwable cause) {
        return new NfoException(LocalizedText.of("nfo.unwritable").with("file", nfoFile), cause);
    }
}
