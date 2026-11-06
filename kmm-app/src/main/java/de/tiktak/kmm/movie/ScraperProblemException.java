package de.tiktak.kmm.movie;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.api.Scraper;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.web.LocalizedProblemException;

/**
 * A scraper is unknown or not set up, or its provider reports an error.
 */
public class ScraperProblemException extends LocalizedProblemException {

    private ScraperProblemException(HttpStatus status, LocalizedText title, LocalizedText detail, Throwable cause) {
        super(status, title, detail, cause);
    }

    private ScraperProblemException(HttpStatus status, String key, LocalizedText detail) {
        this(status, LocalizedText.of(key + ".title"), detail, null);
    }

    static ScraperProblemException unknown(String scraperId) {
        return new ScraperProblemException(HttpStatus.NOT_FOUND, "movie.scraper.unknown",
                LocalizedText.of("movie.scraper.unknown.detail").with("id", scraperId));
    }

    /** @param hint what is missing, as the plugin words it: a translatable text or plain text */
    static ScraperProblemException notConfigured(Scraper scraper, Object hint) {
        return new ScraperProblemException(HttpStatus.UNPROCESSABLE_CONTENT,
                LocalizedText.of("movie.scraper.notConfigured.title").with("scraper", scraper.displayName()),
                LocalizedReasons.hint(hint), null);
    }

    /** The provider cannot be reached or refuses: from KMM's point of view a gateway error. */
    static ScraperProblemException providerFailed(Scraper scraper, ScraperException cause) {
        return new ScraperProblemException(HttpStatus.BAD_GATEWAY,
                LocalizedText.of("movie.scraper.providerFailed.title").with("scraper", scraper.displayName()),
                cause.localizedText(), cause);
    }

    static ScraperProblemException noMetadataScraper() {
        return new ScraperProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "movie.scraper.noMetadataScraper",
                LocalizedText.of("movie.scraper.noMetadataScraper.detail"));
    }

    static ScraperProblemException notIdentified(Scraper scraper, String title) {
        return new ScraperProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "movie.scraper.notIdentified",
                LocalizedText.of("movie.scraper.notIdentified.detail")
                        .with("title", title)
                        .with("scraper", scraper.displayName()));
    }

    static ScraperProblemException noMovieSetScraper() {
        return new ScraperProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "movie.scraper.noMovieSetScraper",
                LocalizedText.of("movie.scraper.noMovieSetScraper.detail"));
    }

    static ScraperProblemException missingIdsForTrailers() {
        return new ScraperProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "movie.trailer.missingIds",
                LocalizedText.of("movie.trailer.missingIds.detail"));
    }

    static ScraperProblemException invalidTrailer(String url) {
        return new ScraperProblemException(HttpStatus.BAD_REQUEST, "movie.trailer.invalid",
                LocalizedText.of("movie.trailer.invalid.detail").with("url", url));
    }

    static ScraperProblemException notFoundAtProvider(Scraper scraper, String providerId) {
        return new ScraperProblemException(HttpStatus.NOT_FOUND, "movie.scraper.notFoundAtProvider",
                LocalizedText.of("movie.scraper.notFoundAtProvider.detail")
                        .with("scraper", scraper.displayName())
                        .with("id", providerId));
    }
}
