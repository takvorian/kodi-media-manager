package de.tiktak.kmm.job;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.web.LocalizedProblemException;

/**
 * A job with the requested ID is not known (any more). Answered as HTTP 404.
 */
public class JobNotFoundException extends LocalizedProblemException {

    JobNotFoundException(UUID jobId) {
        super(HttpStatus.NOT_FOUND, LocalizedText.of("job.notFound.title"),
                LocalizedText.of("job.notFound.detail").with("id", jobId), null);
    }
}
