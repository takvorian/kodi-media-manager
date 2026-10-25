package de.tiktak.kmm.job;

/**
 * Schnittstelle eines laufenden Jobs zur Job-Verwaltung: Fortschritt melden und auf Abbruch prüfen.
 */
public interface JobContext {

    /**
     * @param done    erledigte Schritte
     * @param total   Gesamtzahl der Schritte oder 0, wenn unbekannt
     * @param message aktueller Arbeitsschritt
     */
    void reportProgress(int done, int total, String message);

    boolean isCancellationRequested();

    /**
     * Beendet den Job, falls ein Abbruch angefordert wurde. Lange Jobs rufen das regelmäßig auf.
     *
     * @throws JobCancelledException wenn der Job abgebrochen werden soll
     */
    default void throwIfCancellationRequested() {
        if (isCancellationRequested()) {
            throw new JobCancelledException();
        }
    }
}
