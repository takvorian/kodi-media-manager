package de.tiktak.kmm.movie;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.web.LocalizedProblemException;

/**
 * There is no movie with the requested ID. Answered as HTTP 404.
 */
public class MovieNotFoundException extends LocalizedProblemException {

    MovieNotFoundException(long movieId) {
        super(HttpStatus.NOT_FOUND, LocalizedText.of("movie.notFound.title"),
                LocalizedText.of("movie.notFound.detail").with("id", movieId), null);
    }
}
