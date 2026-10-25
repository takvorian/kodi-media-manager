package de.tiktak.kmm.artwork;

import java.nio.file.Path;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.plugin.api.ImageType;

/**
 * Ein Bild kann nicht gesucht, geladen oder gespeichert werden. Die Meldungen sind für Nutzer formuliert.
 */
public class ArtworkProblemException extends ErrorResponseException {

    protected ArtworkProblemException(HttpStatus status, String title, String detail, Throwable cause) {
        super(status, cause);
        getBody().setTitle(title);
        getBody().setDetail(detail);
    }

    static ArtworkProblemException unsupportedType(ImageType type) {
        return new ArtworkProblemException(HttpStatus.BAD_REQUEST, "Bildart nicht unterstützt",
                "Die Bildart %s wird für Filme nicht verwaltet.".formatted(type), null);
    }

    static ArtworkProblemException missingIds() {
        return new ArtworkProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "Film nicht eindeutig zugeordnet",
                "Für die Bildsuche wird eine TMDB- oder IMDb-ID benötigt. Bitte zuerst „Online suchen“ nutzen.",
                null);
    }

    static ArtworkProblemException imageMissing(ImageType type) {
        return new ArtworkProblemException(HttpStatus.NOT_FOUND, "Kein Bild vorhanden",
                "Für diesen Film gibt es kein Bild der Art %s.".formatted(type), null);
    }

    static ArtworkProblemException unwritable(Path file, Throwable cause) {
        return new ArtworkProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "Bild konnte nicht gespeichert werden",
                "%s kann nicht geschrieben werden. Bitte Schreibrechte im Filmordner prüfen.".formatted(file), cause);
    }
}
