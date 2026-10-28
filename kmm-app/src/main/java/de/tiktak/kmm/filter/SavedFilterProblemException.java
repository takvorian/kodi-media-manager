package de.tiktak.kmm.filter;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Ein gespeicherter Filter existiert nicht, sein Name ist vergeben oder seine Bedingungen sind zu lang.
 */
public class SavedFilterProblemException extends ErrorResponseException {

    private SavedFilterProblemException(HttpStatus status, String title, String detail) {
        super(status);
        getBody().setTitle(title);
        getBody().setDetail(detail);
    }

    static SavedFilterProblemException notFound(long id) {
        return new SavedFilterProblemException(HttpStatus.NOT_FOUND, "Filter nicht gefunden",
                "Es gibt keinen gespeicherten Filter mit der ID %d.".formatted(id));
    }

    static SavedFilterProblemException nameTaken(String name) {
        return new SavedFilterProblemException(HttpStatus.CONFLICT, "Name vergeben",
                "Es gibt schon einen Filter „%s“.".formatted(name));
    }

    static SavedFilterProblemException tooLong() {
        return new SavedFilterProblemException(HttpStatus.BAD_REQUEST, "Filter zu groß",
                "Der Filter hat zu viele oder zu lange Bedingungen.");
    }
}
