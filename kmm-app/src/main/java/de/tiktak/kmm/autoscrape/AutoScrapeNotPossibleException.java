package de.tiktak.kmm.autoscrape;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.web.LocalizedProblemException;

/**
 * A source or season cannot be scraped automatically. Answered as HTTP 422.
 */
public class AutoScrapeNotPossibleException extends LocalizedProblemException {

    AutoScrapeNotPossibleException(MediaSource source) {
        this(LocalizedText.of("autoScrape.sourceNotPossible.title"),
                LocalizedText.of("autoScrape.sourceNotPossible.detail").with("source", source.getName())
                        .with("mediaType", source.getMediaType().name()));
    }

    private AutoScrapeNotPossibleException(LocalizedText title, LocalizedText detail) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, title, detail, null);
    }

    static AutoScrapeNotPossibleException seasonWithoutEpisodes(String showTitle, int season) {
        return new AutoScrapeNotPossibleException(LocalizedText.of("autoScrape.seasonNotPossible.title"),
                LocalizedText.of("autoScrape.seasonNotPossible.detail").with("title", showTitle)
                        .with("season", season));
    }
}
