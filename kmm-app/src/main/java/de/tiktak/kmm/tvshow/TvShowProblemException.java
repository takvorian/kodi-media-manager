package de.tiktak.kmm.tvshow;

import java.nio.file.Path;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.plugin.api.Scraper;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.source.MediaSource;

/**
 * Fehler beim Scannen oder Bearbeiten von Serien, als Problem Detail für die Oberfläche.
 */
public class TvShowProblemException extends ErrorResponseException {

    private TvShowProblemException(HttpStatus status, String title, String detail, Throwable cause) {
        super(status, cause);
        getBody().setTitle(title);
        getBody().setDetail(detail);
    }

    static TvShowProblemException showNotFound(long showId) {
        return new TvShowProblemException(HttpStatus.NOT_FOUND, "Serie nicht gefunden",
                "Es gibt keine Serie mit der ID %d.".formatted(showId), null);
    }

    static TvShowProblemException episodeNotFound(long episodeId) {
        return new TvShowProblemException(HttpStatus.NOT_FOUND, "Episode nicht gefunden",
                "Es gibt keine Episode mit der ID %d.".formatted(episodeId), null);
    }

    static TvShowProblemException notATvSource(MediaSource source) {
        return new TvShowProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "Quelle kann nicht gescannt werden",
                "Die Quelle '%s' enthält keine Serien (%s).".formatted(source.getName(), source.getMediaType()), null);
    }

    static TvShowProblemException unreachable(MediaSource source) {
        return new TvShowProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "Quelle kann nicht gescannt werden",
                "Das Verzeichnis '%s' der Quelle '%s' ist nicht erreichbar.".formatted(source.getPath(),
                        source.getName()), null);
    }

    static TvShowProblemException nfoChangedOutside(Path file, NfoException cause) {
        return new TvShowProblemException(HttpStatus.CONFLICT, "NFO außerhalb von KMM geändert",
                "%s wurde außerhalb von KMM geändert und lässt sich nicht lesen (%s). KMM überschreibt sie deshalb "
                        .formatted(file, cause.getMessage()) + "nicht; bitte die Datei prüfen.", cause);
    }

    static TvShowProblemException nfoNotWritable(NfoException cause) {
        return new TvShowProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "NFO konnte nicht geschrieben werden",
                cause.getMessage() + " Bitte Schreibrechte im Serienordner prüfen.", cause);
    }

    static TvShowProblemException unknownScraper(String scraperId) {
        return new TvShowProblemException(HttpStatus.NOT_FOUND, "Scraper nicht gefunden",
                "Es ist kein Serien-Scraper mit der ID '%s' installiert und aktiv.".formatted(scraperId), null);
    }

    static TvShowProblemException scraperNotConfigured(Scraper scraper, String hint) {
        return new TvShowProblemException(HttpStatus.UNPROCESSABLE_CONTENT,
                "%s ist nicht eingerichtet".formatted(scraper.displayName()), hint, null);
    }

    /** Der Anbieter ist nicht erreichbar oder lehnt ab: aus Sicht von KMM ein Gateway-Fehler. */
    static TvShowProblemException providerFailed(Scraper scraper, ScraperException cause) {
        return new TvShowProblemException(HttpStatus.BAD_GATEWAY,
                "%s meldet einen Fehler".formatted(scraper.displayName()), cause.getMessage(), cause);
    }

    static TvShowProblemException notFoundAtProvider(Scraper scraper, String providerId) {
        return new TvShowProblemException(HttpStatus.NOT_FOUND, "Treffer nicht gefunden",
                "%s kennt den Eintrag '%s' nicht.".formatted(scraper.displayName(), providerId), null);
    }

    static TvShowProblemException notIdentified(Scraper scraper, String title) {
        return new TvShowProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "Serie nicht zugeordnet",
                "„%s“ ist bei %s nicht eindeutig zugeordnet. Bitte zuerst über die Online-Suche übernehmen."
                        .formatted(title, scraper.displayName()), null);
    }
}
