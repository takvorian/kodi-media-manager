import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { translate } from '@jsverse/transloco';
import { EMPTY, Observable, catchError, filter, switchMap, take, tap } from 'rxjs';

import { LocalizedText, localize } from '../i18n/localized-text';
import { Job, isRunning, unsuccessfulJobSummary } from '../jobs/job';
import { JobStore } from '../jobs/job-store';
import { problemMessage } from './problem-message';
import {
  RemovalChoice,
  RemovalDialog,
  RemovalDialogData,
  RemovalHelpChapter,
} from './removal-dialog';

/** Jobs that remove entries start with this key, e.g. `removal:episode:42`. */
export const REMOVAL_JOB_KEY_PREFIX = 'removal:';
const SNACKBAR_DURATION_MS = 10000;
const DIALOG_WIDTH = '720px';

/** Preview of a removal: nothing has been changed yet. */
export interface RemovalPlan {
  readonly entries: readonly LocalizedText[];
  /** Absolute paths of the files deleted along with the entries. */
  readonly files: readonly string[];
  /** Folders removed along with them. */
  readonly directories: readonly string[];
  readonly notes: readonly LocalizedText[];
  /** False if files lie outside the source or cannot be read; then only the library entries can go. */
  readonly canDeleteFiles: boolean;
}

/** Result of a removal job. */
export interface RemovalResult {
  readonly removedEntries: number;
  readonly deletedFiles: number;
  readonly failures: readonly LocalizedText[];
}

/** What is to be removed: the preview and the call that starts the job. */
export interface RemovalTarget {
  /** Title of the dialog, already translated. */
  readonly title: string;
  readonly helpChapter: RemovalHelpChapter;
  readonly plan: Observable<RemovalPlan>;
  readonly remove: (deleteFiles: boolean) => Observable<Job>;
}

export function isRemovalJob(job: Job): boolean {
  return job.key.startsWith(REMOVAL_JOB_KEY_PREFIX);
}

/** Summary of a finished removal job in the active UI language. */
export function removalSummary(job: Job): string {
  if (job.status !== 'SUCCEEDED') {
    return unsuccessfulJobSummary(job);
  }
  const result = job.result as RemovalResult;
  const parts = [translate('removal.summary.removed', { count: result.removedEntries })];
  if (result.deletedFiles > 0) {
    parts.push(translate('removal.summary.deleted', { count: result.deletedFiles }));
  }
  if (result.failures.length > 0) {
    parts.push(
      translate('removal.summary.failures', {
        count: result.failures.length,
        failures: result.failures.map(localize).join('; '),
      }),
    );
  }
  return parts.join(' ');
}

/**
 * Removes episodes, seasons, TV shows and movies: shows the preview, asks how to remove, starts the job and reports
 * its end. Lives as long as the app, so the report also comes after leaving the page.
 */
@Injectable({ providedIn: 'root' })
export class RemovalService {
  private readonly dialog = inject(MatDialog);
  private readonly jobStore = inject(JobStore);
  private readonly snackBar = inject(MatSnackBar);

  /** @returns the started job; nothing if the user cancels or the server refuses */
  remove(target: RemovalTarget): Observable<Job> {
    return target.plan.pipe(
      switchMap((plan) =>
        this.dialog
          .open<RemovalDialog, RemovalDialogData, RemovalChoice>(RemovalDialog, {
            data: { title: target.title, plan, helpChapter: target.helpChapter },
            width: DIALOG_WIDTH,
            autoFocus: 'dialog',
          })
          .afterClosed(),
      ),
      filter((choice): choice is RemovalChoice => !!choice),
      switchMap((choice) => target.remove(choice.deleteFiles)),
      tap((job) => {
        this.jobStore.update(job);
        this.reportWhenDone(job);
      }),
      catchError((error: unknown) => {
        this.notify(problemMessage(error));
        return EMPTY;
      }),
    );
  }

  /** A very short job may have ended before the answer to its start arrived. */
  private reportWhenDone(job: Job): void {
    const finished = this.jobStore.jobs().find((known) => known.id === job.id && !isRunning(known));
    if (finished) {
      this.notify(removalSummary(finished));
      return;
    }
    this.jobStore.completed
      .pipe(
        filter((done) => done.id === job.id),
        take(1),
      )
      .subscribe((done) => this.notify(removalSummary(done)));
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}
