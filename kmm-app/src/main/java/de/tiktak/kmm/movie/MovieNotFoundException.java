package de.tiktak.kmm.movie;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Ein Film mit der angefragten ID existiert nicht. Wird als HTTP 404 beantwortet.
 */
public class MovieNotFoundException extends ErrorResponseException {

    MovieNotFoundException(long movieId) {
        super(HttpStatus.NOT_FOUND);
        getBody().setTitle("Film nicht gefunden");
        getBody().setDetail("Es gibt keinen Film mit der ID %d.".formatted(movieId));
    }
}
