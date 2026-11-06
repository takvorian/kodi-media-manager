package de.tiktak.kmm.web;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.i18n.Localized;
import de.tiktak.kmm.i18n.LocalizedText;

/**
 * An error answered as RFC 9457 problem whose title and detail the user interface translates. The response carries
 * the English texts in {@code title}/{@code detail} for other clients and logs, plus the translatable texts in the
 * properties {@code titleText}/{@code detailText}.
 */
public class LocalizedProblemException extends ErrorResponseException implements Localized {

    private final transient LocalizedText detail;

    protected LocalizedProblemException(HttpStatusCode status, LocalizedText title, LocalizedText detail,
            Throwable cause) {
        super(status, LocalizedProblems.problem(status, title, detail), cause);
        this.detail = detail;
    }

    @Override
    public LocalizedText localizedText() {
        return detail;
    }
}
