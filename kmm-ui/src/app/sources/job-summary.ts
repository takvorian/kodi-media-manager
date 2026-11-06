import { translate } from '@jsverse/transloco';

import { Job } from '../jobs/job';

/** Separates the counts in a summary; the same in every UI language. */
const PART_SEPARATOR = ', ';

/** Summary of a finished job with its counts, e.g. "Scan „Filme“ abgeschlossen: 3 neu, …". */
export function finishedJobSummary(job: Job, parts: readonly string[]): string {
  return translate('sources.job.finished', { title: job.title, result: joinParts(parts) });
}

/** Summary of a cancelled or failed job; the title and the message come from the server. */
export function unsuccessfulJobSummary(job: Job): string {
  return job.status === 'CANCELLED'
    ? translate('sources.job.cancelled', { title: job.title })
    : translate('sources.job.failed', {
        title: job.title,
        message: job.message ?? translate('sources.job.unknownError'),
      });
}

/** Joins the counts of a summary. */
export function joinParts(parts: readonly string[]): string {
  return parts.join(PART_SEPARATOR);
}
