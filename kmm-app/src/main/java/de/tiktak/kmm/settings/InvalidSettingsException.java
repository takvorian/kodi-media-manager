package de.tiktak.kmm.settings;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.web.LocalizedProblemException;

/**
 * Settings that look valid one by one but do not fit, such as an invalid name pattern. Answered as HTTP 422.
 */
public class InvalidSettingsException extends LocalizedProblemException {

    private static final LocalizedText TITLE = LocalizedText.of("settings.invalid.title");

    InvalidSettingsException(LocalizedText detail) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, TITLE, detail, null);
    }
}
