package de.tiktak.kmm.movie;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.web.LocalizedProblemException;

/**
 * A movie set does not exist or its name is taken.
 */
public class MovieSetProblemException extends LocalizedProblemException {

    private MovieSetProblemException(HttpStatus status, String key, LocalizedText detail) {
        super(status, LocalizedText.of(key + ".title"), detail, null);
    }

    static MovieSetProblemException notFound(long setId) {
        return new MovieSetProblemException(HttpStatus.NOT_FOUND, "movieSet.notFound",
                LocalizedText.of("movieSet.notFound.detail").with("id", setId));
    }

    static MovieSetProblemException nameTaken(String name) {
        return new MovieSetProblemException(HttpStatus.CONFLICT, "movieSet.nameTaken",
                LocalizedText.of("movieSet.nameTaken.detail").with("name", name));
    }
}
