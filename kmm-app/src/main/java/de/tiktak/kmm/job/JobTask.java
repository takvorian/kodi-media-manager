package de.tiktak.kmm.job;

/**
 * The actual work of a job.
 *
 * @param <T> result type; delivered to the user interface in the {@link JobSnapshot}
 */
@FunctionalInterface
public interface JobTask<T> {

    T run(JobContext context);
}
