package de.tiktak.kmm.autoscrape;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.source.MediaSource;

/**
 * Eine Quelle kann nicht automatisch gescrapt werden. Wird als HTTP 422 beantwortet.
 */
public class AutoScrapeNotPossibleException extends ErrorResponseException {

    AutoScrapeNotPossibleException(MediaSource source) {
        super(HttpStatus.UNPROCESSABLE_CONTENT);
        getBody().setTitle("Quelle kann nicht gescrapt werden");
        getBody().setDetail("Die Quelle '%s' enthält keine Filme (%s).".formatted(source.getName(), source.getMediaType()));
    }
}
