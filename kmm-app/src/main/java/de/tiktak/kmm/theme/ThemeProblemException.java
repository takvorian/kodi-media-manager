package de.tiktak.kmm.theme;

import java.nio.file.Path;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.plugin.api.ScraperException;

/**
 * Titelmusik kann nicht gesucht, geladen oder gespeichert werden. Die Meldungen sind für Nutzer formuliert.
 */
public class ThemeProblemException extends ErrorResponseException {

    private ThemeProblemException(HttpStatus status, String title, String detail, Throwable cause) {
        super(status, cause);
        getBody().setTitle(title);
        getBody().setDetail(detail);
    }

    static ThemeProblemException unknownScraper(String scraperId) {
        return new ThemeProblemException(HttpStatus.BAD_REQUEST, "Anbieter nicht gefunden",
                "Es ist kein Titelmusik-Anbieter mit der ID '%s' installiert und aktiv.".formatted(scraperId), null);
    }

    static ThemeProblemException downloadFailed(ScraperException cause) {
        return new ThemeProblemException(HttpStatus.BAD_GATEWAY, "Titelmusik nicht geladen", cause.getMessage(),
                cause);
    }

    static ThemeProblemException themeMissing() {
        return new ThemeProblemException(HttpStatus.NOT_FOUND, "Keine Titelmusik",
                "Es gibt keine theme.mp3.", null);
    }

    static ThemeProblemException unwritable(Path file, Throwable cause) {
        return new ThemeProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "Titelmusik nicht gespeichert",
                "%s kann nicht geschrieben werden. Bitte Schreibrechte im Ordner prüfen.".formatted(file), cause);
    }
}
