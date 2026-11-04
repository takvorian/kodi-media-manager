package de.tiktak.kmm.autoscrape;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.source.MediaSource;

/**
 * Eine Quelle oder Staffel kann nicht automatisch gescrapt werden. Wird als HTTP 422 beantwortet.
 */
public class AutoScrapeNotPossibleException extends ErrorResponseException {

    AutoScrapeNotPossibleException(MediaSource source) {
        this("Quelle kann nicht gescrapt werden", "Die Quelle '%s' enthält weder Filme noch Serien (%s)."
                .formatted(source.getName(), source.getMediaType()));
    }

    private AutoScrapeNotPossibleException(String title, String detail) {
        super(HttpStatus.UNPROCESSABLE_CONTENT);
        getBody().setTitle(title);
        getBody().setDetail(detail);
    }

    static AutoScrapeNotPossibleException seasonWithoutEpisodes(String showTitle, int season) {
        return new AutoScrapeNotPossibleException("Staffel kann nicht gescrapt werden",
                "„%s“ hat keine Episoden in Staffel %d.".formatted(showTitle, season));
    }
}
