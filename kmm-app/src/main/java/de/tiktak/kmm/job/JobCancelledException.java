package de.tiktak.kmm.job;

/**
 * Thrown by a job that ends early on request.
 */
public class JobCancelledException extends RuntimeException {

    public JobCancelledException() {
        super("Job cancelled", null, false, false);
    }
}
