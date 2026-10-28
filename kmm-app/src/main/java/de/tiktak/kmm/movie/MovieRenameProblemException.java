package de.tiktak.kmm.movie;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.scanner.naming.NamePatternException;
import de.tiktak.kmm.source.MediaSource;

/**
 * Umbenennen ist so nicht möglich. Wird als HTTP 422 beantwortet.
 */
public class MovieRenameProblemException extends ErrorResponseException {

    private static final String TITLE = "Umbenennen nicht möglich";

    private MovieRenameProblemException(String detail) {
        super(HttpStatus.UNPROCESSABLE_CONTENT);
        getBody().setTitle(TITLE);
        getBody().setDetail(detail);
    }

    static MovieRenameProblemException invalidPattern(String label, NamePatternException e) {
        return new MovieRenameProblemException("%s: %s".formatted(label, e.getMessage()));
    }

    static MovieRenameProblemException folderInFilePattern() {
        return new MovieRenameProblemException("Das Dateimuster darf keine Ordner (/) enthalten.");
    }

    static MovieRenameProblemException notAMovieSource(MediaSource source) {
        return new MovieRenameProblemException("Die Quelle '%s' enthält keine Filme.".formatted(source.getName()));
    }
}
