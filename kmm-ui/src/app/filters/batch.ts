import { translate } from '@jsverse/transloco';

import { Job, unsuccessfulJobSummary } from '../jobs/job';
import { localize } from '../i18n/localized-text';

export interface ListChange {
  readonly add: readonly string[];
  readonly remove: readonly string[];
}

/** Sets a field (`value`) or clears it (`clear`); without either it stays unchanged. */
export interface FieldChange {
  readonly clear: boolean;
  readonly value: string | null;
}

/** What the batch editor reports: genres and tags plus the single fields of the list. */
export interface BatchChanges {
  readonly genres: ListChange;
  readonly tags: ListChange;
  readonly fields: Readonly<Record<string, FieldChange>>;
}

/** A single field of the batch edit, e.g. `mpaa` with “Certification”. */
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

/** Sums up a finished batch edit job for a snackbar message, in the active UI language. */
export function batchSummary(job: Job): string {
  if (job.status !== 'SUCCEEDED') {
    return unsuccessfulJobSummary(job);
  }
  const result = job.result as BatchResult;
  const parts = [
    translate('filters.batch.summary', {
      title: localize(job.title),
      changed: result.changed,
      unchanged: result.unchanged,
    }),
  ];
  if (result.failed > 0) {
    parts.push(
      translate('filters.batch.failures', {
        failed: result.failed,
        failures: result.failures.join('; ').replace(/\.$/, ''),
      }),
    );
  }
  return `${parts.join(', ')}.`;
}
