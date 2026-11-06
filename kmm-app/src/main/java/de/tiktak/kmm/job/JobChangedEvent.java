package de.tiktak.kmm.job;

/**
 * A job was started, reported progress or has finished.
 */
public record JobChangedEvent(JobSnapshot job) {
}
