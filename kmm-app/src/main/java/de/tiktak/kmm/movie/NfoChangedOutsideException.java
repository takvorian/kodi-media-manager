package de.tiktak.kmm.movie;

import java.nio.file.Path;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.nfo.NfoException;

/**
 * Die NFO eines Films wurde außerhalb von KMM geändert und lässt sich nicht lesen. KMM überschreibt sie dann nicht,
 * damit nichts verloren geht. Wird als HTTP 409 beantwortet.
 */
public class NfoChangedOutsideException extends ErrorResponseException {

    NfoChangedOutsideException(Path file, NfoException cause) {
        super(HttpStatus.CONFLICT, cause);
        getBody().setTitle("NFO außerhalb von KMM geändert");
        getBody().setDetail("%s wurde außerhalb von KMM geändert und lässt sich nicht lesen (%s). KMM überschreibt "
                .formatted(file, cause.getMessage()) + "sie deshalb nicht; bitte die Datei prüfen.");
    }
}
