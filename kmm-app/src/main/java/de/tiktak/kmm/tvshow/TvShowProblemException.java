package de.tiktak.kmm.tvshow;

import java.nio.file.Path;
import java.util.List;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.plugin.api.Scraper;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.web.LocalizedProblemException;
import de.tiktak.kmm.web.LocalizedTexts;

/**
 * Errors when scanning or editing TV shows, as problem detail for the user interface.
 */
public class TvShowProblemException extends LocalizedProblemException {

    private static final String KEY = "tvShow.";

    private TvShowProblemException(HttpStatus status, LocalizedText title, LocalizedText detail, Throwable cause) {
        super(status, title, detail, cause);
    }

    private TvShowProblemException(HttpStatus status, String key, LocalizedText detail, Throwable cause) {
        this(status, LocalizedText.of(KEY + key + ".title"), detail, cause);
    }

    static TvShowProblemException showNotFound(long showId) {
        return new TvShowProblemException(HttpStatus.NOT_FOUND, "notFound",
                LocalizedText.of("tvShow.notFound.detail").with("id", showId), null);
    }

    static TvShowProblemException episodeNotFound(long episodeId) {
        return new TvShowProblemException(HttpStatus.NOT_FOUND, "episodeNotFound",
                LocalizedText.of("tvShow.episodeNotFound.detail").with("id", episodeId), null);
    }

    static TvShowProblemException notATvSource(MediaSource source) {
        return new TvShowProblemException(HttpStatus.UNPROCESSABLE_CONTENT, LocalizedText.of("tvShow.scan.title"),
                LocalizedText.of("tvShow.scan.notATvSource").with("source", source.getName())
                        .with("mediaType", source.getMediaType().name()), null);
    }

    static TvShowProblemException unreachable(MediaSource source) {
        return new TvShowProblemException(HttpStatus.UNPROCESSABLE_CONTENT, LocalizedText.of("tvShow.scan.title"),
                LocalizedText.of("tvShow.scan.unreachable").with("path", source.getPath())
                        .with("source", source.getName()), null);
    }

    static TvShowProblemException nfoChangedOutside(Path file, NfoException cause) {
        return new TvShowProblemException(HttpStatus.CONFLICT, "nfoChangedOutside",
                LocalizedText.of("tvShow.nfoChangedOutside.detail").with("file", file)
                        .with("reason", LocalizedTexts.of(cause)), cause);
    }

    static TvShowProblemException nfoNotWritable(NfoException cause) {
        return new TvShowProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "nfoNotWritable",
                LocalizedText.of("tvShow.nfoNotWritable.detail").with("reason", LocalizedTexts.of(cause)), cause);
    }

    static TvShowProblemException nfosNotWritten(List<String> files) {
        return new TvShowProblemException(HttpStatus.INTERNAL_SERVER_ERROR, "nfosNotWritten",
                LocalizedText.of("tvShow.nfosNotWritten.detail").with("files", String.join(", ", files)), null);
    }

    static TvShowProblemException noScraper() {
        return new TvShowProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "noScraper",
                LocalizedText.of("tvShow.noScraper.detail"), null);
    }

    static TvShowProblemException missingIdsForTrailers() {
        return new TvShowProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "missingIdsForTrailers",
                LocalizedText.of("tvShow.missingIdsForTrailers.detail"), null);
    }

    static TvShowProblemException invalidTrailer(String url) {
        return new TvShowProblemException(HttpStatus.BAD_REQUEST, "invalidTrailer",
                LocalizedText.of("tvShow.invalidTrailer.detail").with("url", url), null);
    }

    static TvShowProblemException unknownScraper(String scraperId) {
        return new TvShowProblemException(HttpStatus.NOT_FOUND, "unknownScraper",
                LocalizedText.of("tvShow.unknownScraper.detail").with("id", scraperId), null);
    }

    /**
     * @param hint what the scraper is missing, as plain string or translatable text
     */
    static TvShowProblemException scraperNotConfigured(Scraper scraper, Object hint) {
        return new TvShowProblemException(HttpStatus.UNPROCESSABLE_CONTENT,
                LocalizedText.of("tvShow.scraperNotConfigured.title").with("scraper", scraper.displayName()),
                LocalizedTexts.asText(hint), null);
    }

    /** The provider cannot be reached or rejects the request: from KMM's point of view a gateway error. */
    static TvShowProblemException providerFailed(Scraper scraper, ScraperException cause) {
        return new TvShowProblemException(HttpStatus.BAD_GATEWAY,
                LocalizedText.of("tvShow.providerFailed.title").with("scraper", scraper.displayName()),
                cause.localizedText(), cause);
    }

    static TvShowProblemException notFoundAtProvider(Scraper scraper, String providerId) {
        return new TvShowProblemException(HttpStatus.NOT_FOUND, "notFoundAtProvider",
                LocalizedText.of("tvShow.notFoundAtProvider.detail").with("scraper", scraper.displayName())
                        .with("id", providerId), null);
    }

    static TvShowProblemException notIdentified(Scraper scraper, String title) {
        return new TvShowProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "notIdentified",
                LocalizedText.of("tvShow.notIdentified.detail").with("title", title)
                        .with("scraper", scraper.displayName()), null);
    }
}
