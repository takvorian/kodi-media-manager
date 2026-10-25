package de.tiktak.kmm.source;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Der Pfad einer Quelle ist kein erreichbares Verzeichnis. Wird als HTTP 422 beantwortet.
 */
public class InvalidMediaSourcePathException extends ErrorResponseException {

    public InvalidMediaSourcePathException(String path) {
        super(HttpStatus.UNPROCESSABLE_CONTENT);
        getBody().setTitle("Ungültiger Quellpfad");
        getBody().setDetail("'%s' ist kein erreichbares Verzeichnis.".formatted(path));
    }
}
