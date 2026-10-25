package de.tiktak.kmm.source;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Eine neue Quelle kollidiert mit einer vorhandenen. Wird als HTTP 409 beantwortet.
 */
public class MediaSourceConflictException extends ErrorResponseException {

    private static final String TITLE = "Quelle kollidiert mit vorhandener Quelle";

    private MediaSourceConflictException(String detail) {
        super(HttpStatus.CONFLICT);
        getBody().setTitle(TITLE);
        getBody().setDetail(detail);
    }

    static MediaSourceConflictException nameTaken(String name) {
        return new MediaSourceConflictException("Es gibt bereits eine Quelle mit dem Namen '%s'.".formatted(name));
    }

    /**
     * Überlappende Quellen würden dieselben Dateien doppelt erfassen.
     */
    static MediaSourceConflictException overlaps(String path, MediaSource existing) {
        return new MediaSourceConflictException("Der Pfad '%s' überschneidet sich mit der Quelle '%s' (%s)."
                .formatted(path, existing.getName(), existing.getPath()));
    }
}
