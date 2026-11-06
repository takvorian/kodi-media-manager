import { translate } from '@jsverse/transloco';

import { Job, unsuccessfulJobSummary } from '../jobs/job';
import { localize } from '../i18n/localized-text';

/** Which entries of a source are read; must match StreamDetailsScope. */
export type StreamDetailsScope = 'MISSING' | 'ALL';

/** Prefixes of the job keys; must match StreamDetailsService. */
export const STREAM_DETAILS_JOB_KEY_PREFIX = 'stream-details:';
export const NEW_FILES_STREAM_DETAILS_JOB_KEY_PREFIX = 'stream-details-new:';
export const SEASON_STREAM_DETAILS_JOB_KEY_PREFIX = 'stream-details-season:';

/** Per TV show at most one season is read at a time. */
export function seasonStreamDetailsJobKey(showId: number): string {
  return `${SEASON_STREAM_DETAILS_JOB_KEY_PREFIX}${showId}`;
}

export function streamDetailsJobKey(sourceId: number): string {
  return `${STREAM_DETAILS_JOB_KEY_PREFIX}${sourceId}`;
}

export function newFilesStreamDetailsJobKey(sourceId: number): string {
  return `${NEW_FILES_STREAM_DETAILS_JOB_KEY_PREFIX}${sourceId}`;
}

/** Result of reading a source; the server shortens the list of failures. */
export interface StreamDetailsJobResult {
  readonly sourceId: number;
  readonly updated: number;
  readonly skipped: number;
  readonly failed: number;
  readonly failures: readonly string[];
}

/** Result of reading the episodes of a TV show after the online search. */
export interface ShowStreamDetailsResult {
  readonly updated: number;
  readonly failures: readonly string[];
}

/** Number of failures with the first one as an example. */
function failuresText(count: number, failures: readonly string[]): string {
  return translate('mediaInfo.failures', { count, example: failures[0] });
}

/**
 * Message after reading the episodes, in the active UI language; without read files and failures there is
 * nothing to report.
 */
export function showStreamDetailsSummary(result: ShowStreamDetailsResult | null): string | null {
  if (!result || (result.updated === 0 && result.failures.length === 0)) {
    return null;
  }
  const read = translate('mediaInfo.episodesRead', { updated: result.updated });
  return result.failures.length > 0
    ? `${read} ${failuresText(result.failures.length, result.failures)}.`
    : read;
}

/** Sums up a finished “technical details” job for a snackbar message, in the active UI language. */
export function streamDetailsSummary(job: Job): string {
  if (job.status !== 'SUCCEEDED') {
    return unsuccessfulJobSummary(job);
  }
  const result = job.result as StreamDetailsJobResult;
  const parts = [
    translate('mediaInfo.jobDone', { title: localize(job.title), updated: result.updated }),
  ];
  if (result.skipped > 0) {
    parts.push(translate('mediaInfo.skipped', { skipped: result.skipped }));
  }
  if (result.failed > 0) {
    parts.push(failuresText(result.failed, result.failures));
  }
  return `${parts.join(', ')}.`;
}
