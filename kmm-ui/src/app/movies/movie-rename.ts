import { Job } from '../jobs/job';

/** Präfix des Job-Schlüssels beim Umbenennen; muss zu MovieRenameService.JOB_KEY_PREFIX passen. */
export const MOVIE_RENAME_JOB_KEY_PREFIX = 'movie-rename:';

export function movieRenameJobKey(sourceId: number): string {
  return `${MOVIE_RENAME_JOB_KEY_PREFIX}${sourceId}`;
}

/** Muster für Ordner und Dateien eines Films. */
export interface MovieRenamePatterns {
  readonly folder: string;
  readonly file: string;
}

export type MovieRenameStatus = 'UNCHANGED' | 'RENAME' | 'CONFLICT' | 'FAILED';

export interface FileRename {
  readonly currentName: string;
  readonly targetName: string;
}

/** Was das Umbenennen eines Films ändern würde bzw. geändert hat. */
export interface MovieRenamePlan {
  readonly movieId: number;
  readonly title: string;
  readonly currentDirectory: string;
  readonly targetDirectory: string;
  readonly renames: readonly FileRename[];
  readonly status: MovieRenameStatus;
  readonly message: string | null;
}

export interface MovieRenameResult {
  readonly sourceId: number;
  readonly renamed: number;
  readonly unchanged: number;
  readonly skipped: number;
  readonly failed: number;
  readonly problems: readonly string[];
}

export const RENAME_STATUS_LABELS: Record<MovieRenameStatus, string> = {
  UNCHANGED: 'unverändert',
  RENAME: 'wird umbenannt',
  CONFLICT: 'Konflikt',
  FAILED: 'nicht möglich',
};

/** Ob sich der Ordner des Films ändert. */
export function movesFolder(plan: MovieRenamePlan): boolean {
  return plan.currentDirectory !== plan.targetDirectory;
}

/** Text für die Bestätigung, bevor ein einzelner Film umbenannt wird. */
export function renameConfirmation(plan: MovieRenamePlan): string {
  const lines = [`„${plan.title}“ umbenennen?`];
  if (movesFolder(plan)) {
    lines.push('', `Ordner: ${plan.currentDirectory}`, `→ ${plan.targetDirectory}`);
  }
  if (plan.renames.length > 0) {
    lines.push(
      '',
      'Dateien:',
      ...plan.renames.map((rename) => `${rename.currentName} → ${rename.targetName}`),
    );
  }
  return lines.join('\n');
}

/** Fasst einen beendeten Job „Filme umbenennen“ für eine Snackbar-Meldung zusammen. */
export function movieRenameSummary(job: Job): string {
  if (job.status === 'SUCCEEDED') {
    const result = job.result as MovieRenameResult;
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
