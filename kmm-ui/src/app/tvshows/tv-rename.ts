import { translate } from '@jsverse/transloco';

import { Job } from '../jobs/job';
import { localize, LocalizedText } from '../i18n/localized-text';

/** Prefix of the job key when renaming; must match TvShowRenameService.JOB_KEY_PREFIX. */
export const TV_RENAME_JOB_KEY_PREFIX = 'tv-rename:';

export function tvRenameJobKey(sourceId: number): string {
  return `${TV_RENAME_JOB_KEY_PREFIX}${sourceId}`;
}

/** Patterns for show folders, season folders and episode files. */
export interface TvRenamePatterns {
  readonly showFolder: string;
  readonly seasonFolder: string;
  readonly episodeFile: string;
}

export type TvRenameStatus = 'UNCHANGED' | 'RENAME' | 'CONFLICT' | 'FAILED';

export interface TvFileRename {
  readonly currentName: string;
  readonly targetName: string;
}

/** An episode file with its companion files; folders relative to the show folder. */
export interface TvEpisodeFilePlan {
  readonly episodes: string;
  readonly currentFolder: string;
  readonly targetFolder: string;
  readonly renames: readonly TvFileRename[];
  readonly status: TvRenameStatus;
  readonly message: LocalizedText | null;
}

/** What renaming a TV show would change or has changed. */
export interface TvShowRenamePlan {
  readonly showId: number;
  readonly title: string;
  readonly currentDirectory: string;
  readonly targetDirectory: string;
  readonly files: readonly TvEpisodeFilePlan[];
  readonly status: TvRenameStatus;
  readonly message: LocalizedText | null;
}

export interface TvRenameResult {
  readonly sourceId: number;
  readonly renamed: number;
  readonly unchanged: number;
  readonly skipped: number;
  readonly failed: number;
  readonly problems: readonly LocalizedText[];
}

/** Translation key of the label per status. */
export const TV_RENAME_STATUS_KEYS: Record<TvRenameStatus, string> = {
  UNCHANGED: 'tvshows.rename.status.UNCHANGED',
  RENAME: 'tvshows.rename.status.RENAME',
  CONFLICT: 'tvshows.rename.status.CONFLICT',
  FAILED: 'tvshows.rename.status.FAILED',
};

/** Folder relative to the show folder, readable. */
export function folderLabel(folder: string): string {
  return folder === '' ? translate('tvshows.rename.showFolderLabel') : folder;
}

/** Text for the confirmation before a single TV show is renamed. */
export function tvRenameConfirmation(plan: TvShowRenamePlan): string {
  const lines = [translate('tvshows.rename.confirmQuestion', { title: plan.title })];
  if (plan.currentDirectory !== plan.targetDirectory) {
    lines.push(
      '',
      translate('tvshows.rename.confirmFolder', { folder: plan.currentDirectory }),
      `→ ${plan.targetDirectory}`,
    );
  }
  const files = plan.files.filter((file) => file.status === 'RENAME');
  if (files.length > 0) {
    lines.push('', translate('tvshows.rename.confirmFiles', { count: files.length }));
  }
  const conflicts = plan.files.length - files.length;
  if (conflicts > 0) {
    lines.push(translate('tvshows.rename.confirmConflicts', { count: conflicts }));
  }
  return lines.join('\n');
}

/** Summarizes a finished job “rename TV shows” for a snackbar; the job title comes from the backend. */
export function tvRenameSummary(job: Job): string {
  if (job.status === 'SUCCEEDED') {
    const result = job.result as TvRenameResult;
    return translate('tvshows.rename.succeeded', {
      title: localize(job.title),
      renamed: result.renamed,
      unchanged: result.unchanged,
      skipped: result.skipped,
      failed: result.failed,
    });
  }
  return job.status === 'CANCELLED'
    ? translate('tvshows.rename.cancelled', { title: localize(job.title) })
    : translate('tvshows.rename.failed', {
        title: localize(job.title),
        message: job.message ? localize(job.message) : translate('tvshows.rename.unknownError'),
      });
}
