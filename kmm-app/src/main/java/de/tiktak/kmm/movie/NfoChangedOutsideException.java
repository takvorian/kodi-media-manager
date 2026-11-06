package de.tiktak.kmm.movie;

import java.nio.file.Path;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.web.LocalizedProblemException;

/**
 * The NFO of a movie was changed outside KMM and cannot be read. KMM then does not overwrite it, so nothing gets
 * lost. Answered as HTTP 409.
 */
public class NfoChangedOutsideException extends LocalizedProblemException {

    NfoChangedOutsideException(Path file, NfoException cause) {
        super(HttpStatus.CONFLICT, LocalizedText.of("movie.nfo.changedOutside.title"),
                LocalizedText.of("movie.nfo.changedOutside.detail")
                        .with("file", file)
                        .with("reason", cause.localizedText()),
                cause);
    }
}
