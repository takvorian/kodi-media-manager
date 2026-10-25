package de.tiktak.kmm.job;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Ein Job mit der angefragten ID ist nicht (mehr) bekannt. Wird als HTTP 404 beantwortet.
 */
public class JobNotFoundException extends ErrorResponseException {

    JobNotFoundException(UUID jobId) {
        super(HttpStatus.NOT_FOUND);
        getBody().setTitle("Job nicht gefunden");
        getBody().setDetail("Es gibt keinen Job mit der ID %s.".formatted(jobId));
    }
}
