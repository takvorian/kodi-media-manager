package de.tiktak.kmm.job;

/**
 * Wird von einem Job geworfen, der auf Anforderung vorzeitig endet.
 */
public class JobCancelledException extends RuntimeException {

    public JobCancelledException() {
        super("Job cancelled", null, false, false);
    }
}
