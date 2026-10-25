package de.tiktak.kmm.source;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Eine Quelle mit der angefragten ID existiert nicht. Wird als HTTP 404 beantwortet.
 */
public class MediaSourceNotFoundException extends ErrorResponseException {

    MediaSourceNotFoundException(long sourceId) {
        super(HttpStatus.NOT_FOUND);
        getBody().setTitle("Quelle nicht gefunden");
        getBody().setDetail("Es gibt keine Quelle mit der ID %d.".formatted(sourceId));
    }
}
