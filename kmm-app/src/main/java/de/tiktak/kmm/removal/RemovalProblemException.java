package de.tiktak.kmm.removal;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.web.LocalizedProblemException;

/**
 * A removal is not possible as requested, as problem detail for the user interface.
 */
public class RemovalProblemException extends LocalizedProblemException {

    private RemovalProblemException(HttpStatus status, String key, LocalizedText detail) {
        super(status, LocalizedText.of("removal." + key + ".title"), detail, null);
    }

    /** Some files lie outside the source or cannot be read; only the library entries may be removed. */
    public static RemovalProblemException filesBlocked() {
        return new RemovalProblemException(HttpStatus.CONFLICT, "filesBlocked",
                LocalizedText.of("removal.filesBlocked.detail"));
    }

    public static RemovalProblemException seasonNotFound(String showTitle, int season) {
        return new RemovalProblemException(HttpStatus.NOT_FOUND, "seasonNotFound",
                LocalizedText.of("removal.seasonNotFound.detail").with("title", showTitle).with("season", season));
    }
}
