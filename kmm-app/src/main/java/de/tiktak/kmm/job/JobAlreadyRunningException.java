package de.tiktak.kmm.job;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.web.LocalizedProblemException;

/**
 * A job is already running for the same key. Answered as HTTP 409.
 */
public class JobAlreadyRunningException extends LocalizedProblemException {

    public JobAlreadyRunningException(JobSnapshot running) {
        super(HttpStatus.CONFLICT, LocalizedText.of("job.alreadyRunning.title"),
                LocalizedText.of("job.alreadyRunning.detail").with("job", running.title()), null);
        getBody().setProperty("jobId", running.id());
    }
}
