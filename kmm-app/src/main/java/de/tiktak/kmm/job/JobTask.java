package de.tiktak.kmm.job;

/**
 * Die eigentliche Arbeit eines Jobs.
 *
 * @param <T> Ergebnistyp; wird im {@link JobSnapshot} an die Oberfläche ausgeliefert
 */
@FunctionalInterface
public interface JobTask<T> {

    T run(JobContext context);
}
