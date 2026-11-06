import { translate } from '@jsverse/transloco';

import { localize, LocalizedText } from '../i18n/localized-text';

export type JobStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';

/** State of a background job as REST and SSE deliver it. `title` and `message` are translated with `localize`. */
export interface Job {
  readonly id: string;
  readonly key: string;
  readonly title: LocalizedText;
  readonly status: JobStatus;
  readonly done: number;
  readonly total: number;
  readonly message: LocalizedText | null;
  readonly result: unknown;
  readonly startedAt: string;
  readonly finishedAt: string | null;
}

export function isRunning(job: Job): boolean {
  return job.status === 'RUNNING';
}

/** Progress in percent, or `null` while the total is unknown. */
export function progressPercent(job: Job): number | null {
  const percentFactor = 100;
  return job.total > 0 ? Math.round((job.done / job.total) * percentFactor) : null;
}

/** Message for a job that was cancelled or failed, in the active UI language. */
export function unsuccessfulJobSummary(job: Job): string {
  return job.status === 'CANCELLED'
    ? translate('jobs.cancelled', { title: localize(job.title) })
    : translate('jobs.failed', {
        title: localize(job.title),
        message: job.message ? localize(job.message) : translate('problems.unknown'),
      });
}
