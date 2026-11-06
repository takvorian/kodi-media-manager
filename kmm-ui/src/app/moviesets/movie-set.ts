import { translate } from '@jsverse/transloco';

import { Job } from '../jobs/job';
import { LocalizedText, localize } from '../i18n/localized-text';

/** Job key of the set changes; must match MovieSetJobService.JOB_KEY. */
export const MOVIE_SET_JOB_KEY = 'moviesets';

export type SetSortMethod = 'YEAR' | 'TITLE';

/** Translation key of the label of each sort method. */
export const SORT_METHOD_KEYS: Record<SetSortMethod, string> = {
  YEAR: 'moviesets.sortMethod.YEAR',
  TITLE: 'moviesets.sortMethod.TITLE',
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

/** Search result of a TMDB collection. */
export interface MovieSetCandidate {
  readonly providerId: string;
  readonly title: string;
  readonly originalTitle: string | null;
  readonly overview: string | null;
  readonly posterUrl: string | null;
}

/** Result of “Complete all sets”. */
export interface MovieSetCompletionResult {
  readonly overviews: number;
  readonly images: number;
  readonly withoutCollection: number;
  readonly problems: readonly LocalizedText[];
}

export interface MovieSetChangeResult {
  readonly changed: number;
  readonly unchanged: number;
  readonly failed: number;
  readonly failures: readonly LocalizedText[];
}

/** Summarizes a finished set job for a snackbar message. */
export function movieSetJobSummary(job: Job): string {
  if (job.status === 'SUCCEEDED' && isCompletion(job.result)) {
    const result = job.result;
    const key =
      result.problems.length > 0
        ? 'moviesets.summary.completedWithNotes'
        : 'moviesets.summary.completed';
    return translate(key, {
      title: localize(job.title),
      overviews: result.overviews,
      images: result.images,
      skipped: result.withoutCollection,
      notes: result.problems.map(localize).join('; '),
    });
  }
  if (job.status === 'SUCCEEDED') {
    const result = job.result as MovieSetChangeResult;
    const key =
      result.failed > 0 ? 'moviesets.summary.changedWithFailures' : 'moviesets.summary.changed';
    return translate(key, {
      title: localize(job.title),
      changed: result.changed,
      failed: result.failed,
      failures: result.failures.map(localize).join('; '),
    });
  }
  return job.status === 'CANCELLED'
    ? translate('moviesets.summary.cancelled', { title: localize(job.title) })
    : translate('moviesets.summary.failed', {
        title: localize(job.title),
        reason: job.message ? localize(job.message) : translate('moviesets.summary.unknownError'),
      });
}

function isCompletion(result: unknown): result is MovieSetCompletionResult {
  return typeof result === 'object' && result !== null && 'overviews' in result;
}
