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

/** Suchtreffer einer TMDB-Sammlung. */
export interface MovieSetCandidate {
  readonly providerId: string;
  readonly title: string;
  readonly originalTitle: string | null;
  readonly overview: string | null;
  readonly posterUrl: string | null;
}

/** Ergebnis von „Alle Sets ergänzen“. */
export interface MovieSetCompletionResult {
  readonly overviews: number;
  readonly images: number;
  readonly withoutCollection: number;
  readonly problems: readonly string[];
}

export interface MovieSetChangeResult {
  readonly changed: number;
  readonly unchanged: number;
  readonly failed: number;
  readonly failures: readonly string[];
}

/** Fasst einen beendeten Set-Job für eine Snackbar-Meldung zusammen. */
export function movieSetJobSummary(job: Job): string {
  if (job.status === 'SUCCEEDED' && isCompletion(job.result)) {
    const problems =
      job.result.problems.length > 0 ? ` Hinweise: ${job.result.problems.join('; ')}` : '';
    return (
      `${job.title}: ${job.result.overviews} Beschreibungen und ${job.result.images} Bilder geholt, ` +
      `${job.result.withoutCollection} Sets ohne TMDB-ID übersprungen.${problems}`
    );
  }
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

function isCompletion(result: unknown): result is MovieSetCompletionResult {
  return typeof result === 'object' && result !== null && 'overviews' in result;
}
