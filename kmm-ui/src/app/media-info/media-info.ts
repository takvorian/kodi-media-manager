import { Job } from '../jobs/job';

/** Welche Einträge einer Quelle ausgelesen werden; muss zu StreamDetailsScope passen. */
export type StreamDetailsScope = 'MISSING' | 'ALL';

/** Präfixe der Job-Schlüssel; müssen zu StreamDetailsService passen. */
export const STREAM_DETAILS_JOB_KEY_PREFIX = 'stream-details:';
export const NEW_FILES_STREAM_DETAILS_JOB_KEY_PREFIX = 'stream-details-new:';

export function streamDetailsJobKey(sourceId: number): string {
  return `${STREAM_DETAILS_JOB_KEY_PREFIX}${sourceId}`;
}

export function newFilesStreamDetailsJobKey(sourceId: number): string {
  return `${NEW_FILES_STREAM_DETAILS_JOB_KEY_PREFIX}${sourceId}`;
}

/** Ergebnis des Auslesens einer Quelle; die Fehlerliste ist serverseitig gekürzt. */
export interface StreamDetailsJobResult {
  readonly sourceId: number;
  readonly updated: number;
  readonly skipped: number;
  readonly failed: number;
  readonly failures: readonly string[];
}

/** Ergebnis des Auslesens der Episoden einer Serie nach der Online-Suche. */
export interface ShowStreamDetailsResult {
  readonly updated: number;
  readonly failures: readonly string[];
}

/** Meldung nach dem Auslesen der Episoden; ohne ausgelesene Dateien und Fehler gibt es nichts zu melden. */
export function showStreamDetailsSummary(result: ShowStreamDetailsResult | null): string | null {
  if (!result || (result.updated === 0 && result.failures.length === 0)) {
    return null;
  }
  const failed = result.failures.length > 0 ? ` ${result.failures.length} Fehler (z. B. ${result.failures[0]}).` : '';
  return `Technische Angaben von ${result.updated} Episodendateien ausgelesen.${failed}`;
}

/** Fasst einen beendeten Job „Technische Angaben“ für eine Snackbar-Meldung zusammen. */
export function streamDetailsSummary(job: Job): string {
  if (job.status === 'SUCCEEDED') {
    const result = job.result as StreamDetailsJobResult;
    const skipped = result.skipped > 0 ? `, ${result.skipped} hatten schon Angaben` : '';
    const failed = result.failed > 0 ? `, ${result.failed} Fehler (z. B. ${result.failures[0]})` : '';
    return `${job.title} abgeschlossen: ${result.updated} ausgelesen${skipped}${failed}.`;
  }
  return job.status === 'CANCELLED'
    ? `${job.title} abgebrochen.`
    : `${job.title} fehlgeschlagen: ${job.message ?? 'Unbekannter Fehler'}`;
}
