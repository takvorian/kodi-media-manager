package de.tiktak.kmm.source;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.web.LocalizedProblemException;

/**
 * There is no source with the requested ID. Answered as HTTP 404.
 */
public class MediaSourceNotFoundException extends LocalizedProblemException {

    MediaSourceNotFoundException(long sourceId) {
        super(HttpStatus.NOT_FOUND, LocalizedText.of("source.notFound.title"),
                LocalizedText.of("source.notFound.detail").with("id", sourceId), null);
    }
}
