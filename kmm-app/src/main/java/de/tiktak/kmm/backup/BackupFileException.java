package de.tiktak.kmm.backup;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.web.LocalizedProblemException;
import org.springframework.http.HttpStatus;

/**
 * The backup file is missing or cannot be used. Answered as HTTP 400.
 */
class BackupFileException extends LocalizedProblemException {

    private static final LocalizedText TITLE = LocalizedText.of("backup.unusable.title");

    BackupFileException(LocalizedText message) {
        this(message, null);
    }

    BackupFileException(LocalizedText message, Throwable cause) {
        super(HttpStatus.BAD_REQUEST, TITLE, message, cause);
    }

    /** The file does not follow the backup format. */
    static BackupFileException invalid(Throwable cause) {
        return new BackupFileException(LocalizedText.of("backup.invalidFile"), cause);
    }
}
