package de.tiktak.kmm.job;

/**
 * Ein Job wurde gestartet, hat Fortschritt gemeldet oder ist beendet.
 */
public record JobChangedEvent(JobSnapshot job) {
}
