package de.tiktak.kmm.settings;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Einstellungen, die einzeln gültig aussehen, aber nicht passen, etwa ein ungültiges Namensmuster. Wird als
 * HTTP 422 beantwortet.
 */
public class InvalidSettingsException extends ErrorResponseException {

    private static final String TITLE = "Ungültige Einstellungen";

    InvalidSettingsException(String detail) {
        super(HttpStatus.UNPROCESSABLE_CONTENT);
        getBody().setTitle(TITLE);
        getBody().setDetail(detail);
    }
}
