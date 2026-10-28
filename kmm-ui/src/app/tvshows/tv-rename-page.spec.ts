import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';

import { Job } from '../jobs/job';
import { JobStore } from '../jobs/job-store';
import { TvShowRenamePlan } from './tv-rename';
import { TvRenamePage } from './tv-rename-page';

describe('TvRenamePage', () => {
  const patterns = {
    showFolder: '{show} ({year})',
    seasonFolder: 'Staffel {season}',
    episodeFile: '{show} - S{season:2}E{episode:2}',
  };
  const plans: TvShowRenamePlan[] = [
    {
      showId: 1,
      title: 'ALF',
      currentDirectory: '/serien/ALF (1986)',
      targetDirectory: '/serien/ALF (1986)',
      files: [],
      status: 'UNCHANGED',
      message: null,
    },
    {
      showId: 2,
      title: 'Lost',
      currentDirectory: '/serien/lost',
      targetDirectory: '/serien/Lost (2004)',
      files: [
        {
          episodes: 'S01E01E02',
          currentFolder: '',
          targetFolder: 'Staffel 1',
          renames: [{ currentName: 'lost.mkv', targetName: 'Lost - S01E01E02.mkv' }],
          status: 'RENAME',
          message: null,
        },
        {
          episodes: 'S01E03',
          currentFolder: 'Season 1',
          targetFolder: 'Season 1',
          renames: [],
          status: 'FAILED',
          message: 'Das Muster ergibt keinen Namen.',
        },
      ],
      status: 'RENAME',
      message: null,
    },
    {
      showId: 3,
      title: 'Monk',
      currentDirectory: '/serien/monk',
      targetDirectory: '/serien/Monk (2002)',
      files: [],
      status: 'CONFLICT',
      message: 'Der Ordner existiert bereits.',
    },
  ];

  let fixture: ComponentFixture<TvRenamePage>;
  let httpTesting: HttpTestingController;
  let snackBarMessages: string[];
  const completed = new Subject<Job>();
  let running: Job | undefined;
  const updated: Job[] = [];

  beforeEach(() => {
    snackBarMessages = [];
    running = undefined;
    updated.length = 0;
    TestBed.configureTestingModule({
      imports: [TvRenamePage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: MatSnackBar,
          useValue: { open: (message: string) => snackBarMessages.push(message) },
        },
        {
          provide: JobStore,
          useValue: {
            connect: () => undefined,
            completed,
            runningJob: () => running,
            update: (job: Job) => updated.push(job),
          },
        },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TvRenamePage);
    fixture.componentRef.setInput('id', '8');
  });

  afterEach(() => httpTesting.verify());

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function button(selector: string): HTMLButtonElement {
    return element().querySelector<HTMLButtonElement>(selector)!;
  }

  function text(selector: string): string[] {
    return [...element().querySelectorAll(selector)].map((node) =>
      node.textContent!.replace(/\s+/g, ' ').trim(),
    );
  }

  async function load(): Promise<void> {
    fixture.detectChanges();
    httpTesting.expectOne({ method: 'GET', url: '/api/v1/settings' }).flush({
      rename: {
        tvShowFolder: patterns.showFolder,
        seasonFolder: patterns.seasonFolder,
        episodeFile: patterns.episodeFile,
      },
    });
    await fixture.whenStable();
  }

  async function preview(answer: TvShowRenamePlan[] = plans): Promise<void> {
    button('.preview-button').click();
    const request = httpTesting.expectOne({
      method: 'POST',
      url: '/api/v1/sources/8/tv-rename/preview',
    });
    expect(request.request.body).toEqual(patterns);
    request.flush(answer);
    await fixture.whenStable();
  }

  it('previews shows with their season folders and episode files', async () => {
    await load();

    await preview();

    expect(element().querySelector('.summary')!.textContent).toContain('1 umzubenennen');
    expect(text('.show-title')).toEqual(['Lost', 'Monk']);
    expect(text('.files .season')).toEqual(['(Serienordner) → Staffel 1', 'Season 1']);
    expect(text('.files .names')).toEqual([
      'lost.mkv → Lost - S01E01E02.mkv',
      'Das Muster ergibt keinen Namen.',
    ]);
    expect(text('.show .message')).toContain('Der Ordner existiert bereits.');
    expect(button('.rename-button').textContent).toContain('1 umbenennen');

    element().querySelector<HTMLInputElement>('.only-changes input')!.click();
    await fixture.whenStable();
    expect(text('.show-title')).toEqual(['ALF', 'Lost', 'Monk']);
  });

  it('says when all shows are named right already', async () => {
    await load();

    await preview([plans[0]]);

    expect(text('.empty')).toEqual(['Alle 1 Serien heißen schon so.']);
  });

  it('renames the selected shows and previews again when the job is done', async () => {
    await load();
    await preview();
    button('.select-none').click();
    await fixture.whenStable();
    expect(button('.rename-button').disabled).toBe(true);
    button('.select-all').click();
    await fixture.whenStable();

    button('.rename-button').click();
    const request = httpTesting.expectOne({ method: 'POST', url: '/api/v1/sources/8/tv-rename' });
    expect(request.request.body).toEqual({ patterns, showIds: [2] });
    const job = {
      id: 'j',
      key: 'tv-rename:8',
      title: 'Serien umbenennen „Serien“',
      status: 'RUNNING',
    } as Job;
    request.flush(job);
    expect(updated).toEqual([job]);

    completed.next({
      ...job,
      status: 'SUCCEEDED',
      result: { sourceId: 8, renamed: 1, unchanged: 1, skipped: 1, failed: 0, problems: [] },
    } as Job);
    httpTesting
      .expectOne({ method: 'POST', url: '/api/v1/sources/8/tv-rename/preview' })
      .flush(plans);

    expect(snackBarMessages).toEqual([
      'Serien umbenennen „Serien“ abgeschlossen: 1 umbenannt, 1 unverändert, 1 übersprungen.',
    ]);
  });

  it('can deselect a single show', async () => {
    await load();
    await preview();

    element().querySelector<HTMLInputElement>('.show-header input')!.click();
    await fixture.whenStable();

    expect(button('.rename-button').disabled).toBe(true);
  });

  it('shows a running job', async () => {
    running = {
      id: 'j',
      key: 'tv-rename:8',
      title: 'x',
      status: 'RUNNING',
      message: 'Lost',
    } as Job;
    await load();

    expect(element().querySelector('.job-progress')!.textContent).toContain('Lost');
    expect(button('.preview-button').disabled).toBe(true);
  });

  it('reports invalid patterns', async () => {
    await load();

    button('.preview-button').click();
    httpTesting
      .expectOne({ method: 'POST', url: '/api/v1/sources/8/tv-rename/preview' })
      .flush(
        { detail: 'Muster für Staffelordner: kaputt' },
        { status: 422, statusText: 'Unprocessable' },
      );

    expect(snackBarMessages).toEqual(['Muster für Staffelordner: kaputt']);
  });
});
