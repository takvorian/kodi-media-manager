import { Job } from '../jobs/job';

/** Job-Schlüssel der Set-Änderungen; muss zu MovieSetJobService.JOB_KEY passen. */
export const MOVIE_SET_JOB_KEY = 'moviesets';

export type SetSortMethod = 'YEAR' | 'TITLE';

export const SORT_METHOD_LABELS: Record<SetSortMethod, string> = {
  YEAR: 'nach Jahr',
  TITLE: 'nach Titel',
};

export interface MovieSetData {
  readonly name: string;
  readonly overview: string | null;
  readonly tmdbCollectionId: string | null;
  readonly sortMethod: SetSortMethod;
}

export interface MovieSetSummary extends MovieSetData {
  readonly id: number;
  readonly movies: number;
}

export interface MovieSetMember {
  readonly id: number;
  readonly title: string;
  readonly year: number | null;
}

export interface MovieSetDetails {
  readonly id: number;
  readonly data: MovieSetData;
  readonly movies: readonly MovieSetMember[];
}

export interface MovieSetChangeResult {
  readonly changed: number;
  readonly unchanged: number;
  readonly failed: number;
  readonly failures: readonly string[];
}

/** Fasst einen beendeten Set-Job für eine Snackbar-Meldung zusammen. */
export function movieSetJobSummary(job: Job): string {
  if (job.status === 'SUCCEEDED') {
    const result = job.result as MovieSetChangeResult;
    const failed =
      result.failed > 0 ? `, ${result.failed} Fehler: ${result.failures.join('; ')}` : '';
    return `${job.title}: ${result.changed} Filme geändert${failed}.`;
  }
  return job.status === 'CANCELLED'
    ? `${job.title} abgebrochen.`
    : `${job.title} fehlgeschlagen: ${job.message ?? 'Unbekannter Fehler'}`;
}
