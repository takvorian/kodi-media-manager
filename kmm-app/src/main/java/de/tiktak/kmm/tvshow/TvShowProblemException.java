package de.tiktak.kmm.tvshow;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.nfo.NfoException;
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

    static TvShowProblemException nfoNotWritable(NfoException cause) {
        return new TvShowProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "NFO konnte nicht geschrieben werden",
                cause.getMessage() + " Bitte Schreibrechte im Serienordner prüfen.", cause);
    }
}
