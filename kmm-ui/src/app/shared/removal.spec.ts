import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoService } from '@jsverse/transloco';
import { Observable, Subject, of, throwError } from 'rxjs';

import { literal } from '../i18n/localized-text';
import { provideI18nTesting } from '../i18n/testing';
import { Job } from '../jobs/job';
import { JobStore } from '../jobs/job-store';
import {
  RemovalPlan,
  RemovalResult,
  RemovalService,
  isRemovalJob,
  removalSummary,
} from './removal';
import { RemovalChoice } from './removal-dialog';

describe('RemovalService', () => {
  const plan: RemovalPlan = {
    entries: [literal('Heat (1995)')],
    files: ['/filme/Heat/Heat.mkv'],
    directories: [],
    notes: [],
    canDeleteFiles: true,
  };
  const started = {
    id: 'r1',
    key: 'removal:movie:1',
    title: literal('Heat entfernen'),
    status: 'RUNNING',
    done: 0,
    total: 2,
    message: null,
    result: null,
    startedAt: '2026-11-10T10:00:00Z',
    finishedAt: null,
  } as Job;
  const result: RemovalResult = {
    removedEntries: 1,
    deletedFiles: 2,
    failures: [{ key: 'removal.deleteFailed', args: { file: 'Heat.nfo', reason: 'gesperrt' } }],
  };
  const finished = { ...started, status: 'SUCCEEDED', result } as Job;

  let snackBarMessages: string[];
  let updated: Job[];
  let dialogData: unknown[];
  let answer: RemovalChoice | undefined;
  const completed = new Subject<Job>();
  const known = signal<Job[]>([]);

  beforeEach(() => {
    snackBarMessages = [];
    updated = [];
    dialogData = [];
    answer = { deleteFiles: true };
    known.set([]);
    TestBed.configureTestingModule({
      providers: [
        provideI18nTesting(),
        {
          provide: MatSnackBar,
          useValue: { open: (message: string) => snackBarMessages.push(message) },
        },
        {
          provide: MatDialog,
          useValue: {
            open: (_component: unknown, config: { data: unknown }) => {
              dialogData.push(config.data);
              return { afterClosed: () => of(answer) };
            },
          },
        },
        {
          provide: JobStore,
          useValue: { completed, jobs: () => known(), update: (job: Job) => updated.push(job) },
        },
      ],
    });
  });

  function remove(start: (deleteFiles: boolean) => Observable<Job>): Job[] {
    const jobs: Job[] = [];
    TestBed.inject(RemovalService)
      .remove({ title: 'Heat entfernen', helpChapter: 'movies', plan: of(plan), remove: start })
      .subscribe((job) => jobs.push(job));
    return jobs;
  }

  it('asks with the preview, starts the job and reports its end', () => {
    const requested: boolean[] = [];

    const jobs = remove((deleteFiles) => {
      requested.push(deleteFiles);
      return of(started);
    });

    expect(dialogData).toEqual([{ title: 'Heat entfernen', plan, helpChapter: 'movies' }]);
    expect(requested).toEqual([true]);
    expect(jobs).toEqual([started]);
    expect(updated).toEqual([started]);
    expect(snackBarMessages).toEqual([]);

    completed.next({ ...finished, id: 'other' });
    completed.next(finished);
    completed.next(finished);

    expect(snackBarMessages).toEqual([
      '1 Eintrag entfernt. 2 Dateien gelöscht. 1 Datei konnte nicht gelöscht werden: ' +
        '„Heat.nfo“ konnte nicht gelöscht werden: gesperrt',
    ]);
  });

  it('reports a job that ended before its start was answered', () => {
    known.set([finished]);

    remove(() => of(started));

    expect(snackBarMessages).toEqual([
      '1 Eintrag entfernt. 2 Dateien gelöscht. 1 Datei konnte nicht gelöscht werden: ' +
        '„Heat.nfo“ konnte nicht gelöscht werden: gesperrt',
    ]);
  });

  it('starts nothing when cancelled', () => {
    answer = undefined;
    let calls = 0;

    const jobs = remove(() => {
      calls++;
      return of(started);
    });

    expect(calls).toBe(0);
    expect(jobs).toEqual([]);
  });

  it('shows why the server refuses', () => {
    const jobs = remove(() =>
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { detailText: { key: 'removal.filesBlocked.detail', args: {} } },
          }),
      ),
    );

    expect(jobs).toEqual([]);
    expect(snackBarMessages).toHaveLength(1);
  });

  it('summarizes in English and tells failed jobs apart', () => {
    TestBed.inject(TranslocoService).setActiveLang('en');

    expect(removalSummary({ ...finished, result: { ...result, failures: [] } })).toBe(
      '1 entry removed. 2 files deleted.',
    );
    expect(
      removalSummary({ ...finished, result: { removedEntries: 3, deletedFiles: 0, failures: [] } }),
    ).toBe('3 entries removed.');
    expect(removalSummary({ ...finished, status: 'CANCELLED' })).toContain('Heat entfernen');
  });

  it('recognizes removal jobs by their key', () => {
    expect(isRemovalJob(started)).toBe(true);
    expect(isRemovalJob({ ...started, key: 'movies-batch' })).toBe(false);
  });
});
