package de.tiktak.kmm.kodi;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.kodi.rpc.KodiException;

/**
 * Fehler beim Verwalten oder Ansprechen der Kodi-Geräte, als Problem Detail für die Oberfläche.
 */
public class KodiProblemException extends ErrorResponseException {

    private KodiProblemException(HttpStatus status, String title, String detail, Throwable cause) {
        super(status, cause);
        getBody().setTitle(title);
        getBody().setDetail(detail);
    }

    static KodiProblemException hostNotFound(long hostId) {
        return new KodiProblemException(HttpStatus.NOT_FOUND, "Kodi-Gerät nicht gefunden",
                "Es gibt kein Kodi-Gerät mit der ID %d.".formatted(hostId), null);
    }

    static KodiProblemException nameTaken(String name) {
        return new KodiProblemException(HttpStatus.CONFLICT, "Name bereits vergeben",
                "Es gibt bereits ein Kodi-Gerät mit dem Namen '%s'.".formatted(name), null);
    }

    static KodiProblemException noHostFor(String path) {
        return new KodiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "Kein passendes Kodi-Gerät",
                "Kein Kodi-Gerät hat eine Pfad-Zuordnung für %s.".formatted(path), null);
    }

    static KodiProblemException noHosts() {
        return new KodiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "Kein Kodi-Gerät",
                "Es ist kein Kodi-Gerät eingerichtet.", null);
    }

    static KodiProblemException noHostLeft() {
        return new KodiProblemException(HttpStatus.BAD_GATEWAY, "Kodi nicht erreichbar",
                "Übersprungen, weil kein Kodi-Gerät mehr antwortet.", null);
    }

    static KodiProblemException refreshFailed(String problems) {
        return new KodiProblemException(HttpStatus.BAD_GATEWAY, "Nicht aktualisiert", problems, null);
    }

    static KodiProblemException unavailable(KodiException cause) {
        return new KodiProblemException(HttpStatus.BAD_GATEWAY, "Kodi nicht erreichbar", cause.getMessage(), cause);
    }
}
