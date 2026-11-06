package de.tiktak.kmm.job;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Interface of a running job to the job management: report progress and check for cancellation.
 */
public interface JobContext {

    /**
     * @param done    completed steps
     * @param total   total number of steps, or 0 if unknown
     * @param message current step
     */
    void reportProgress(int done, int total, LocalizedText message);

    /**
     * Reports a step that is shown as is, such as the name of the file being processed.
     *
     * @param literal text that is not translated
     */
    default void reportProgress(int done, int total, String literal) {
        reportProgress(done, total, LocalizedText.literal(literal));
    }

    /** Reports progress without a current step. */
    default void reportProgress(int done, int total) {
        reportProgress(done, total, (LocalizedText) null);
    }

    boolean isCancellationRequested();

    /**
     * Ends the job if a cancellation was requested. Long jobs call this regularly.
     *
     * @throws JobCancelledException if the job is to be cancelled
     */
    default void throwIfCancellationRequested() {
        if (isCancellationRequested()) {
            throw new JobCancelledException();
        }
    }
}
