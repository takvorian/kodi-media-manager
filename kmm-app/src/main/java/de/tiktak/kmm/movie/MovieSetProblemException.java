package de.tiktak.kmm.movie;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Ein Filmset gibt es nicht oder sein Name ist vergeben.
 */
public class MovieSetProblemException extends ErrorResponseException {

    private MovieSetProblemException(HttpStatus status, String title, String detail) {
        super(status);
        getBody().setTitle(title);
        getBody().setDetail(detail);
    }

    static MovieSetProblemException notFound(long setId) {
        return new MovieSetProblemException(HttpStatus.NOT_FOUND, "Filmset nicht gefunden",
                "Es gibt kein Filmset mit der ID %d.".formatted(setId));
    }

    static MovieSetProblemException nameTaken(String name) {
        return new MovieSetProblemException(HttpStatus.CONFLICT, "Name vergeben",
                "Es gibt schon ein Filmset „%s“.".formatted(name));
    }
}
