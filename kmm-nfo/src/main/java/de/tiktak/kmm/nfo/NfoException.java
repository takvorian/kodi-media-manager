package de.tiktak.kmm.nfo;

import java.nio.file.Path;

/**
 * Eine NFO-Datei kann nicht gelesen oder geschrieben werden.
 */
public class NfoException extends RuntimeException {

    private NfoException(String message, Throwable cause) {
        super(message, cause);
    }

    static NfoException unreadable(Path nfoFile, Throwable cause) {
        return new NfoException("Die NFO-Datei '%s' kann nicht gelesen werden.".formatted(nfoFile), cause);
    }

    static NfoException missingEntry(Path nfoFile) {
        return new NfoException("Die NFO-Datei '%s' fehlt oder enthält den Eintrag nicht.".formatted(nfoFile), null);
    }

    static NfoException unwritable(Path nfoFile, Throwable cause) {
        return new NfoException("Die NFO-Datei '%s' kann nicht geschrieben werden.".formatted(nfoFile), cause);
    }
}
