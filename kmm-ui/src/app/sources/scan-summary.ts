import { translate } from '@jsverse/transloco';

import { Job } from '../jobs/job';
import { finishedJobSummary, unsuccessfulJobSummary } from './job-summary';
import { ScanResult } from './media-source';

const SCAN_COUNTS = 'sources.scanCounts';

/** Summary of a finished scan of a source, a TV show or a season, e.g. "Scan „Filme“ abgeschlossen: 3 neu, …". */
export function scanSummary(job: Job): string {
  return job.status === 'SUCCEEDED'
    ? finishedJobSummary(job, scanCounts(job))
    : unsuccessfulJobSummary(job);
}

/** The counts of a successful scan in the active language; the optional ones only if they are not zero. */
export function scanCounts(job: Job): string[] {
  const result = job.result as ScanResult;
  const unrecognized = result.unrecognizedFiles?.length ?? 0;
  return [
    translate(`${SCAN_COUNTS}.counts`, {
      added: result.added,
      updated: result.updated,
      removed: result.removed,
      unchanged: result.unchanged,
    }),
    ...optionalCount('newShows', result.showsAdded ?? 0),
    ...optionalCount('nfoErrors', result.nfoErrors),
    ...optionalCount('unrecognized', unrecognized),
  ];
}

function optionalCount(key: string, count: number): string[] {
  return count > 0 ? [translate(`${SCAN_COUNTS}.${key}`, { count })] : [];
}
