package de.tiktak.kmm.tvshow;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.scanner.naming.NamePatternException;
import de.tiktak.kmm.source.MediaSource;

/**
 * Umbenennen von Serien ist so nicht möglich. Wird als HTTP 422 beantwortet.
 */
public class TvRenameProblemException extends ErrorResponseException {

    private static final String TITLE = "Umbenennen nicht möglich";

    private TvRenameProblemException(String detail) {
        super(HttpStatus.UNPROCESSABLE_CONTENT);
        getBody().setTitle(TITLE);
        getBody().setDetail(detail);
    }

    static TvRenameProblemException invalidPattern(String label, NamePatternException e) {
        return new TvRenameProblemException("Muster für %s: %s".formatted(label, e.getMessage()));
    }

    static TvRenameProblemException foldersNotAllowed(String label) {
        return new TvRenameProblemException("Muster für %s: Ordner (/) sind hier nicht erlaubt.".formatted(label));
    }

    static TvRenameProblemException notATvSource(MediaSource source) {
        return new TvRenameProblemException("Die Quelle '%s' enthält keine Serien.".formatted(source.getName()));
    }
}
