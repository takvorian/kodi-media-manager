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
                "Es gibt kein Bild der Art %s.".formatted(type), null);
    }

    static ArtworkProblemException unwritable(Path file, Throwable cause) {
        return new ArtworkProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "Bild konnte nicht gespeichert werden",
                "%s kann nicht geschrieben werden. Bitte Schreibrechte im Ordner prüfen.".formatted(file), cause);
    }

    static ArtworkProblemException unsupportedTvType(ImageType type, String target) {
        return new ArtworkProblemException(HttpStatus.BAD_REQUEST, "Bildart nicht unterstützt",
                "Die Bildart %s wird für %s nicht verwaltet.".formatted(type, target), null);
    }

    static ArtworkProblemException missingCollectionId() {
        return new ArtworkProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "Set nicht zugeordnet",
                "Für die Bildsuche wird die TMDB-ID der Sammlung benötigt. Bitte zuerst das Set bei TMDB suchen.",
                null);
    }

    static ArtworkProblemException noMovieSetImageStorage() {
        return new ArtworkProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "Keine Ablage für Set-Bilder",
                "Bitte in den Einstellungen unter „Filmsets“ eine Ablage für Set-Bilder einschalten.", null);
    }

    static ArtworkProblemException missingTvIds() {
        return new ArtworkProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "Serie nicht eindeutig zugeordnet",
                "Für die Bildsuche wird eine TVDB-, TMDB- oder IMDb-ID benötigt. Bitte zuerst „Online suchen“ nutzen.",
                null);
    }
}
