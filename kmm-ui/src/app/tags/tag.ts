import { Job } from '../jobs/job';

/** Job-Schlüssel der Tag-Änderungen; muss zu TagService.JOB_KEY passen. */
export const TAG_JOB_KEY = 'tags';

export interface TagSummary {
  readonly name: string;
  readonly movies: number;
  readonly shows: number;
}

export interface TaggedItem {
  readonly id: number;
  readonly title: string;
}

export interface TaggedItems {
  readonly tag: string;
  readonly movies: readonly TaggedItem[];
  readonly shows: readonly TaggedItem[];
}

export interface TagAssignment {
  readonly tag: string;
  readonly movieIds: readonly number[];
  readonly showIds: readonly number[];
}

export interface TagChangeResult {
  readonly changed: number;
  readonly unchanged: number;
  readonly failed: number;
  readonly failures: readonly string[];
}

/** Fasst einen beendeten Tag-Job für eine Snackbar-Meldung zusammen. */
export function tagJobSummary(job: Job): string {
  if (job.status === 'SUCCEEDED') {
    const result = job.result as TagChangeResult;
    const failed =
      result.failed > 0 ? `, ${result.failed} Fehler: ${result.failures.join('; ')}` : '';
    return `${job.title}: ${result.changed} geändert, ${result.unchanged} unverändert${failed}.`;
  }
  return job.status === 'CANCELLED'
    ? `${job.title} abgebrochen.`
    : `${job.title} fehlgeschlagen: ${job.message ?? 'Unbekannter Fehler'}`;
}
