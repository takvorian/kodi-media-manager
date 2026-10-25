package de.tiktak.kmm.movie;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.nfo.NfoException;

/**
 * Die NFO eines Films kann nicht geschrieben werden, z. B. wegen fehlender Schreibrechte. Die
 * Änderung wird dann nicht gespeichert. Wird als HTTP 422 beantwortet.
 */
public class NfoNotWritableException extends ErrorResponseException {

    NfoNotWritableException(NfoException cause) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, cause);
        getBody().setTitle("NFO konnte nicht geschrieben werden");
        getBody().setDetail(cause.getMessage() + " Bitte Schreibrechte im Filmordner prüfen.");
    }
}
