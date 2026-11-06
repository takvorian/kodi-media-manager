import { translate } from '@jsverse/transloco';

import { Job } from '../jobs/job';
import { localize } from '../i18n/localized-text';

/** Job key of the tag changes; must match TagService.JOB_KEY. */
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

/** Summarizes a finished tag job for a snackbar message. */
export function tagJobSummary(job: Job): string {
  if (job.status === 'SUCCEEDED') {
    const result = job.result as TagChangeResult;
    const key = result.failed > 0 ? 'tags.summary.succeededWithFailures' : 'tags.summary.succeeded';
    return translate(key, {
      title: localize(job.title),
      changed: result.changed,
      unchanged: result.unchanged,
      failed: result.failed,
      failures: result.failures.join('; '),
    });
  }
  return job.status === 'CANCELLED'
    ? translate('tags.summary.cancelled', { title: localize(job.title) })
    : translate('tags.summary.failed', {
        title: localize(job.title),
        reason: job.message ? localize(job.message) : translate('tags.summary.unknownError'),
      });
}
