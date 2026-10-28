import { Job } from '../jobs/job';

/** Präfix des Job-Schlüssels beim Umbenennen; muss zu TvShowRenameService.JOB_KEY_PREFIX passen. */
export const TV_RENAME_JOB_KEY_PREFIX = 'tv-rename:';

export function tvRenameJobKey(sourceId: number): string {
  return `${TV_RENAME_JOB_KEY_PREFIX}${sourceId}`;
}

/** Muster für Serienordner, Staffelordner und Episodendateien. */
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

/** Eine Episodendatei samt Begleitdateien; Ordner relativ zum Serienordner. */
export interface TvEpisodeFilePlan {
  readonly episodes: string;
  readonly currentFolder: string;
  readonly targetFolder: string;
  readonly renames: readonly TvFileRename[];
  readonly status: TvRenameStatus;
  readonly message: string | null;
}

/** Was das Umbenennen einer Serie ändern würde bzw. geändert hat. */
export interface TvShowRenamePlan {
  readonly showId: number;
  readonly title: string;
  readonly currentDirectory: string;
  readonly targetDirectory: string;
  readonly files: readonly TvEpisodeFilePlan[];
  readonly status: TvRenameStatus;
  readonly message: string | null;
}

export interface TvRenameResult {
  readonly sourceId: number;
  readonly renamed: number;
  readonly unchanged: number;
  readonly skipped: number;
  readonly failed: number;
  readonly problems: readonly string[];
}

export const TV_RENAME_STATUS_LABELS: Record<TvRenameStatus, string> = {
  UNCHANGED: 'unverändert',
  RENAME: 'wird umbenannt',
  CONFLICT: 'Konflikt',
  FAILED: 'nicht möglich',
};

/** Ordner relativ zum Serienordner, lesbar. */
export function folderLabel(folder: string): string {
  return folder === '' ? '(Serienordner)' : folder;
}

/** Text für die Bestätigung, bevor eine einzelne Serie umbenannt wird. */
export function tvRenameConfirmation(plan: TvShowRenamePlan): string {
  const lines = [`„${plan.title}“ umbenennen?`];
  if (plan.currentDirectory !== plan.targetDirectory) {
    lines.push('', `Ordner: ${plan.currentDirectory}`, `→ ${plan.targetDirectory}`);
  }
  const files = plan.files.filter((file) => file.status === 'RENAME');
  if (files.length > 0) {
    lines.push('', `${files.length} Episodendateien bekommen neue Namen bzw. Staffelordner.`);
  }
  const conflicts = plan.files.length - files.length;
  if (conflicts > 0) {
    lines.push(`${conflicts} Episodendateien bleiben wegen Konflikten unverändert.`);
  }
  return lines.join('\n');
}

/** Fasst einen beendeten Job „Serien umbenennen“ für eine Snackbar-Meldung zusammen. */
export function tvRenameSummary(job: Job): string {
  if (job.status === 'SUCCEEDED') {
    const result = job.result as TvRenameResult;
    const failed = result.failed > 0 ? `, ${result.failed} fehlgeschlagen` : '';
    return (
      `${job.title} abgeschlossen: ${result.renamed} umbenannt, ${result.unchanged} unverändert, ` +
      `${result.skipped} übersprungen${failed}.`
    );
  }
  return job.status === 'CANCELLED'
    ? `${job.title} abgebrochen.`
    : `${job.title} fehlgeschlagen: ${job.message ?? 'Unbekannter Fehler'}`;
}
