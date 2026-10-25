package de.tiktak.kmm.job;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Für denselben Schlüssel läuft bereits ein Job. Wird als HTTP 409 beantwortet.
 */
public class JobAlreadyRunningException extends ErrorResponseException {

    public JobAlreadyRunningException(JobSnapshot running) {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Job läuft bereits");
        getBody().setDetail("%s läuft bereits.".formatted(running.title()));
        getBody().setProperty("jobId", running.id());
    }
}
