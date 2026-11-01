import { Job } from '../jobs/job';

export interface ListChange {
  readonly add: readonly string[];
  readonly remove: readonly string[];
}

/** Ein Feld setzen (`value`) oder leeren (`clear`); ohne beides bleibt es unverändert. */
export interface FieldChange {
  readonly clear: boolean;
  readonly value: string | null;
}

/** Was der Stapel-Editor meldet: Genres und Tags sowie die einzelnen Felder der Liste. */
export interface BatchChanges {
  readonly genres: ListChange;
  readonly tags: ListChange;
  readonly fields: Readonly<Record<string, FieldChange>>;
}

/** Ein einzelnes Feld der Stapel-Bearbeitung, z. B. `mpaa` mit „Altersfreigabe“. */
export interface BatchField {
  readonly key: BatchFieldKey;
  readonly label: string;
}

export type BatchFieldKey = 'mpaa' | 'edition' | 'videoSource' | 'status';

export interface BatchResult {
  readonly changed: number;
  readonly unchanged: number;
  readonly failed: number;
  readonly failures: readonly string[];
}

/** Fasst einen beendeten Job der Stapel-Bearbeitung für eine Snackbar-Meldung zusammen. */
export function batchSummary(job: Job): string {
  if (job.status === 'SUCCEEDED') {
    const result = job.result as BatchResult;
    const failed =
      result.failed > 0
        ? `, ${result.failed} Fehler: ${result.failures.join('; ').replace(/\.$/, '')}`
        : '';
    return `${job.title}: ${result.changed} geändert, ${result.unchanged} unverändert${failed}.`;
  }
  return job.status === 'CANCELLED'
    ? `${job.title} abgebrochen.`
    : `${job.title} fehlgeschlagen: ${job.message ?? 'Unbekannter Fehler'}`;
}
