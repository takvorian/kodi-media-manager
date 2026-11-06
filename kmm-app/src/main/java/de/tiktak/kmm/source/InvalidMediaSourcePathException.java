package de.tiktak.kmm.source;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.web.LocalizedProblemException;

/**
 * The path of a source is not a reachable directory. Answered as HTTP 422.
 */
public class InvalidMediaSourcePathException extends LocalizedProblemException {

    public InvalidMediaSourcePathException(String path) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, LocalizedText.of("source.invalidPath.title"),
                LocalizedText.of("source.invalidPath.detail").with("path", path), null);
    }
}
