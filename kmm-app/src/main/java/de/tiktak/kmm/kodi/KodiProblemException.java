package de.tiktak.kmm.kodi;

import java.util.List;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.kodi.rpc.KodiException;
import de.tiktak.kmm.web.LocalizedProblemException;

/**
 * Error while managing or contacting the Kodi devices, as problem detail for the user interface.
 */
public class KodiProblemException extends LocalizedProblemException {

    private static final LocalizedText UNAVAILABLE = LocalizedText.of("kodi.unavailable.title");

    private KodiProblemException(HttpStatus status, LocalizedText title, LocalizedText detail, Throwable cause) {
        super(status, title, detail, cause);
    }

    static KodiProblemException hostNotFound(long hostId) {
        return new KodiProblemException(HttpStatus.NOT_FOUND, LocalizedText.of("kodi.hostNotFound.title"),
                LocalizedText.of("kodi.hostNotFound.detail").with("id", hostId), null);
    }

    static KodiProblemException nameTaken(String name) {
        return new KodiProblemException(HttpStatus.CONFLICT, LocalizedText.of("kodi.nameTaken.title"),
                LocalizedText.of("kodi.nameTaken.detail").with("name", name), null);
    }

    static KodiProblemException noHostFor(String path) {
        return new KodiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, LocalizedText.of("kodi.noHostFor.title"),
                LocalizedText.of("kodi.noHostFor.detail").with("path", path), null);
    }

    static KodiProblemException noHosts() {
        return new KodiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, LocalizedText.of("kodi.noHosts.title"),
                LocalizedText.of("kodi.noHosts.detail"), null);
    }

    static KodiProblemException noHostLeft() {
        return new KodiProblemException(HttpStatus.BAD_GATEWAY, UNAVAILABLE, LocalizedText.of("kodi.noHostLeft.detail"),
                null);
    }

    /** @param problems one text per device, e.g. {@code kodi.hostProblem} */
    static KodiProblemException refreshFailed(List<LocalizedText> problems) {
        return new KodiProblemException(HttpStatus.BAD_GATEWAY, LocalizedText.of("kodi.refreshFailed.title"),
                joined(problems), null);
    }

    static KodiProblemException unavailable(KodiException cause) {
        return new KodiProblemException(HttpStatus.BAD_GATEWAY, UNAVAILABLE, cause.localizedText(), cause);
    }

    /** Joins the texts with semicolons, as nested texts so each part stays translatable. */
    private static LocalizedText joined(List<LocalizedText> texts) {
        if (texts.size() == 1) {
            return texts.getFirst();
        }
        return LocalizedText.of("kodi.problems").with("first", texts.getFirst())
                .with("rest", joined(texts.subList(1, texts.size())));
    }
}
