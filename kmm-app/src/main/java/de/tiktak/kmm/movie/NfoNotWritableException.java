package de.tiktak.kmm.movie;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.web.LocalizedProblemException;

/**
 * The NFO of a movie cannot be written, e.g. for lack of write permissions. The change is not saved then.
 * Answered as HTTP 422.
 */
public class NfoNotWritableException extends LocalizedProblemException {

    NfoNotWritableException(NfoException cause) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, LocalizedText.of("movie.nfo.notWritable.title"),
                LocalizedText.of("movie.nfo.notWritable.detail").with("reason", cause.localizedText()),
                cause);
    }
}
