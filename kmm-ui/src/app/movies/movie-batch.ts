import { Job } from '../jobs/job';

/** Job-Schlüssel der Stapel-Bearbeitung; muss zu MovieBatchService.JOB_KEY passen. */
export const MOVIE_BATCH_JOB_KEY = 'movies-batch';

export interface ListChange {
  readonly add: readonly string[];
  readonly remove: readonly string[];
}

/** Ein Feld setzen (`value`) oder leeren (`clear`); ohne beides bleibt es unverändert. */
export interface FieldChange {
  readonly clear: boolean;
  readonly value: string | null;
}

/** Inhalt von POST /api/v1/movies/batch. */
export interface MovieBatchEdit {
  readonly movieIds: readonly number[];
  readonly genres: ListChange;
  readonly tags: ListChange;
  readonly mpaa: FieldChange;
  readonly edition: FieldChange;
  readonly videoSource: FieldChange;
}

export interface MovieBatchResult {
  readonly changed: number;
  readonly unchanged: number;
  readonly failed: number;
  readonly failures: readonly string[];
}

/** Fasst einen beendeten Job der Stapel-Bearbeitung für eine Snackbar-Meldung zusammen. */
export function movieBatchSummary(job: Job): string {
  if (job.status === 'SUCCEEDED') {
    const result = job.result as MovieBatchResult;
    const failed =
      result.failed > 0 ? `, ${result.failed} Fehler: ${result.failures.join('; ')}` : '';
    return `${job.title}: ${result.changed} geändert, ${result.unchanged} unverändert${failed}.`;
  }
  return job.status === 'CANCELLED'
    ? `${job.title} abgebrochen.`
    : `${job.title} fehlgeschlagen: ${job.message ?? 'Unbekannter Fehler'}`;
}
