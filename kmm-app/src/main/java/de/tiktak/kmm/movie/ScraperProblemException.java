package de.tiktak.kmm.movie;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.plugin.api.Scraper;
import de.tiktak.kmm.plugin.api.ScraperException;

/**
 * Ein Scraper ist unbekannt, nicht eingerichtet oder sein Anbieter meldet einen Fehler.
 */
public class ScraperProblemException extends ErrorResponseException {

    private ScraperProblemException(HttpStatus status, String title, String detail, Throwable cause) {
        super(status, cause);
        getBody().setTitle(title);
        getBody().setDetail(detail);
    }

    static ScraperProblemException unknown(String scraperId) {
        return new ScraperProblemException(HttpStatus.NOT_FOUND, "Scraper nicht gefunden",
                "Es ist kein Scraper mit der ID '%s' installiert und aktiv.".formatted(scraperId), null);
    }

    static ScraperProblemException notConfigured(Scraper scraper, String hint) {
        return new ScraperProblemException(HttpStatus.UNPROCESSABLE_CONTENT,
                "%s ist nicht eingerichtet".formatted(scraper.displayName()), hint, null);
    }

    /** Der Anbieter ist nicht erreichbar oder lehnt ab: aus Sicht von KMM ein Gateway-Fehler. */
    static ScraperProblemException providerFailed(Scraper scraper, ScraperException cause) {
        return new ScraperProblemException(HttpStatus.BAD_GATEWAY,
                "%s meldet einen Fehler".formatted(scraper.displayName()), cause.getMessage(), cause);
    }

    static ScraperProblemException noMetadataScraper() {
        return new ScraperProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "Kein Scraper eingerichtet",
                "Es ist kein Metadaten-Scraper eingerichtet, etwa TMDB mit API-Key.", null);
    }

    static ScraperProblemException notIdentified(Scraper scraper, String title) {
        return new ScraperProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "Film nicht zugeordnet",
                "„%s“ ist bei %s nicht eindeutig zugeordnet. Bitte zuerst über die Online-Suche übernehmen."
                        .formatted(title, scraper.displayName()), null);
    }

    static ScraperProblemException noMovieSetScraper() {
        return new ScraperProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "Kein Scraper für Filmsets",
                "Es ist kein Scraper für Filmsets installiert und aktiv, etwa das TMDB-Plugin.", null);
    }

    static ScraperProblemException missingIdsForTrailers() {
        return new ScraperProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "Film nicht eindeutig zugeordnet",
                "Für die Trailersuche wird eine TMDB- oder IMDb-ID benötigt. Bitte zuerst „Online suchen“ nutzen.",
                null);
    }

    static ScraperProblemException invalidTrailer(String url) {
        return new ScraperProblemException(HttpStatus.BAD_REQUEST, "Ungültiger Trailer",
                "„%s“ ist kein Link, den Kodi abspielen kann (plugin://, http(s)://, smb:// oder nfs://)."
                        .formatted(url), null);
    }

    static ScraperProblemException notFoundAtProvider(Scraper scraper, String providerId) {
        return new ScraperProblemException(HttpStatus.NOT_FOUND, "Treffer nicht gefunden",
                "%s kennt den Eintrag '%s' nicht.".formatted(scraper.displayName(), providerId), null);
    }
}
