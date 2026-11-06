import { translate } from '@jsverse/transloco';

import { Job } from '../jobs/job';
import { LocalizedText, localize } from '../i18n/localized-text';

/** Prefix of the job key for renaming; must match MovieRenameService.JOB_KEY_PREFIX. */
export const MOVIE_RENAME_JOB_KEY_PREFIX = 'movie-rename:';

export function movieRenameJobKey(sourceId: number): string {
  return `${MOVIE_RENAME_JOB_KEY_PREFIX}${sourceId}`;
}

/** Patterns for the folder and the files of a movie. */
export interface MovieRenamePatterns {
  readonly folder: string;
  readonly file: string;
}

export type MovieRenameStatus = 'UNCHANGED' | 'RENAME' | 'CONFLICT' | 'FAILED';

export interface FileRename {
  readonly currentName: string;
  readonly targetName: string;
}

/** What renaming a movie would change or has changed. */
export interface MovieRenamePlan {
  readonly movieId: number;
  readonly title: string;
  readonly currentDirectory: string;
  readonly targetDirectory: string;
  readonly renames: readonly FileRename[];
  readonly status: MovieRenameStatus;
  readonly message: LocalizedText | null;
}

export interface MovieRenameResult {
  readonly sourceId: number;
  readonly renamed: number;
  readonly unchanged: number;
  readonly skipped: number;
  readonly failed: number;
  readonly problems: readonly LocalizedText[];
}

/** Translation key of the label of each rename status. */
export const RENAME_STATUS_KEYS: Record<MovieRenameStatus, string> = {
  UNCHANGED: 'movies.rename.status.UNCHANGED',
  RENAME: 'movies.rename.status.RENAME',
  CONFLICT: 'movies.rename.status.CONFLICT',
  FAILED: 'movies.rename.status.FAILED',
};

/** Whether the folder of the movie changes. */
export function movesFolder(plan: MovieRenamePlan): boolean {
  return plan.currentDirectory !== plan.targetDirectory;
}

/** Text for the confirmation before a single movie is renamed. */
export function renameConfirmation(plan: MovieRenamePlan): string {
  const lines = [translate('movies.rename.confirmQuestion', { title: plan.title })];
  if (movesFolder(plan)) {
    lines.push(
      '',
      translate('movies.rename.confirmFolder', { path: plan.currentDirectory }),
      `→ ${plan.targetDirectory}`,
    );
  }
  if (plan.renames.length > 0) {
    lines.push(
      '',
      translate('movies.rename.confirmFiles'),
      ...plan.renames.map((rename) => `${rename.currentName} → ${rename.targetName}`),
    );
  }
  return lines.join('\n');
}

/** Summarizes a finished “Rename movies” job for a snackbar message. */
export function movieRenameSummary(job: Job): string {
  if (job.status === 'SUCCEEDED') {
    const result = job.result as MovieRenameResult;
    const key =
      result.failed > 0
        ? 'movies.rename.summary.succeededWithFailures'
        : 'movies.rename.summary.succeeded';
    return translate(key, {
      title: localize(job.title),
      renamed: result.renamed,
      unchanged: result.unchanged,
      skipped: result.skipped,
      failed: result.failed,
    });
  }
  return job.status === 'CANCELLED'
    ? translate('movies.rename.summary.cancelled', { title: localize(job.title) })
    : translate('movies.rename.summary.failed', {
        title: localize(job.title),
        reason: job.message
          ? localize(job.message)
          : translate('movies.rename.summary.unknownError'),
      });
}
