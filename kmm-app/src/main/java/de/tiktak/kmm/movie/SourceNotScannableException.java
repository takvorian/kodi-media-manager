package de.tiktak.kmm.movie;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.source.MediaSource;

/**
 * Eine Quelle kann nicht nach Filmen durchsucht werden. Wird als HTTP 422 beantwortet.
 */
public class SourceNotScannableException extends ErrorResponseException {

    private static final String TITLE = "Quelle kann nicht gescannt werden";

    private SourceNotScannableException(String detail) {
        super(HttpStatus.UNPROCESSABLE_CONTENT);
        getBody().setTitle(TITLE);
        getBody().setDetail(detail);
    }

    static SourceNotScannableException notAMovieSource(MediaSource source) {
        return new SourceNotScannableException("Die Quelle '%s' enthält keine Filme (%s)."
                .formatted(source.getName(), source.getMediaType()));
    }

    static SourceNotScannableException unreachable(MediaSource source) {
        return new SourceNotScannableException("Das Verzeichnis '%s' der Quelle '%s' ist nicht erreichbar."
                .formatted(source.getPath(), source.getName()));
    }
}
