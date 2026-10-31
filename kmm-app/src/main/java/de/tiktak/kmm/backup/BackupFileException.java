package de.tiktak.kmm.backup;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Die Sicherungsdatei fehlt oder lässt sich nicht verwenden. Wird als HTTP 400 beantwortet.
 */
class BackupFileException extends ErrorResponseException {

    BackupFileException(String message) {
        this(message, null);
    }

    BackupFileException(String message, Throwable cause) {
        super(HttpStatus.BAD_REQUEST, ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message), cause);
        getBody().setTitle("Sicherung nicht verwendbar");
    }
}
