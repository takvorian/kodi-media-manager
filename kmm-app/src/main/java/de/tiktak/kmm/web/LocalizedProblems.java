package de.tiktak.kmm.web;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.i18n.Messages;

/**
 * Builds problem responses with translatable texts, for exception handlers that do not throw a
 * {@link LocalizedProblemException}.
 */
public final class LocalizedProblems {

    /** Property with the translatable title. */
    public static final String TITLE_TEXT = "titleText";
    /** Property with the translatable detail. */
    public static final String DETAIL_TEXT = "detailText";

    private LocalizedProblems() {
    }

    public static ProblemDetail problem(HttpStatusCode status, LocalizedText title, LocalizedText detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, Messages.english(detail));
        problem.setTitle(Messages.english(title));
        problem.setProperty(TITLE_TEXT, title);
        problem.setProperty(DETAIL_TEXT, detail);
        return problem;
    }
}
