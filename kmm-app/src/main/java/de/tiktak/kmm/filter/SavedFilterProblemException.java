package de.tiktak.kmm.filter;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.web.LocalizedProblemException;

/**
 * A saved filter does not exist, its name is taken or its conditions are too long.
 */
public class SavedFilterProblemException extends LocalizedProblemException {

    private SavedFilterProblemException(HttpStatus status, String key, LocalizedText detail) {
        super(status, LocalizedText.of(key + ".title"), detail, null);
    }

    static SavedFilterProblemException notFound(long id) {
        return new SavedFilterProblemException(HttpStatus.NOT_FOUND, "filter.saved.notFound",
                LocalizedText.of("filter.saved.notFound.detail").with("id", id));
    }

    static SavedFilterProblemException nameTaken(String name) {
        return new SavedFilterProblemException(HttpStatus.CONFLICT, "filter.saved.nameTaken",
                LocalizedText.of("filter.saved.nameTaken.detail").with("name", name));
    }

    static SavedFilterProblemException tooLong() {
        return new SavedFilterProblemException(HttpStatus.BAD_REQUEST, "filter.saved.tooLong",
                LocalizedText.of("filter.saved.tooLong.detail"));
    }
}
