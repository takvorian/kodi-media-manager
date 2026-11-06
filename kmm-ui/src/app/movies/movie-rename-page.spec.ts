import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { TranslocoService } from '@jsverse/transloco';

import { provideI18nTesting } from '../i18n/testing';
import { Job } from '../jobs/job';
import { JobStore } from '../jobs/job-store';
import { MovieRenamePlan } from './movie-rename';
import { MovieRenamePage } from './movie-rename-page';

describe('MovieRenamePage', () => {
  const plans: MovieRenamePlan[] = [
    {
      movieId: 1,
      title: 'Alien',
      currentDirectory: '/filme/Alien (1979)',
      targetDirectory: '/filme/Alien (1979)',
      renames: [],
      status: 'UNCHANGED',
      message: null,
    },
    {
      movieId: 2,
      title: 'Heat',
      currentDirectory: '/filme/heat',
      targetDirectory: '/filme/Heat (1995)',
      renames: [{ currentName: 'heat.mkv', targetName: 'Heat.mkv' }],
      status: 'RENAME',
      message: null,
    },
    {
      movieId: 3,
      title: 'Ronin',
      currentDirectory: '/filme/ronin',
      targetDirectory: '/filme/Ronin (1998)',
      renames: [{ currentName: 'ronin.mkv', targetName: 'Ronin.mkv' }],
      status: 'RENAME',
      message: null,
    },
    {
      movieId: 4,
      title: 'Brazil',
      currentDirectory: '/filme/brazil',
      targetDirectory: '/filme/Brazil (1985)',
      renames: [],
      status: 'CONFLICT',
      message: 'Der Ordner existiert bereits.',
    },
  ];

  let fixture: ComponentFixture<MovieRenamePage>;
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
      imports: [MovieRenamePage],
      providers: [
        provideHttpClient(),
        provideI18nTesting(),
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
    fixture = TestBed.createComponent(MovieRenamePage);
    fixture.componentRef.setInput('id', '7');
  });

  afterEach(() => httpTesting.verify());

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function button(selector: string): HTMLButtonElement {
    return element().querySelector<HTMLButtonElement>(selector)!;
  }

  function input(name: string): HTMLInputElement {
    return element().querySelector<HTMLInputElement>(`[name="${name}"]`)!;
  }

  function rows(): string[] {
    return [...element().querySelectorAll('.plans tbody tr')].map((row) =>
      [...row.querySelectorAll('td')]
        .map((cell) =>
          [...cell.childNodes]
            .filter((node) => node.nodeType !== Node.COMMENT_NODE)
            .map((node) => node.textContent!.replace(/\s+/g, ' ').trim())
            .filter(Boolean)
            .join(' '),
        )
        .filter(Boolean)
        .join(' | '),
    );
  }

  async function load(): Promise<void> {
    fixture.detectChanges();
    httpTesting
      .expectOne({ method: 'GET', url: '/api/v1/settings' })
      .flush({ rename: { movieFolder: '{title}[ ({year})]', movieFile: '{title}' } });
    await fixture.whenStable();
  }

  async function preview(answer: MovieRenamePlan[] = plans): Promise<void> {
    button('.preview-button').click();
    const request = httpTesting.expectOne({
      method: 'POST',
      url: '/api/v1/sources/7/movie-rename/preview',
    });
    expect(request.request.body).toEqual({ folder: '{title}[ ({year})]', file: '{title}' });
    request.flush(answer);
    await fixture.whenStable();
  }

  it('takes the patterns from the settings and previews the changes', async () => {
    await load();
    expect(input('folder').value).toBe('{title}[ ({year})]');

    await preview();

    expect(element().querySelector('.summary')!.textContent).toContain('2 umzubenennen');
    expect(element().querySelector('.count-conflict')!.textContent).toContain('1 Konflikt');
    expect(rows()).toEqual([
      'Brazil | /filme/brazil → /filme/Brazil (1985) | Konflikt Der Ordner existiert bereits.',
      'Heat | /filme/heat → /filme/Heat (1995) | heat.mkv → Heat.mkv | wird umbenannt',
      'Ronin | /filme/ronin → /filme/Ronin (1998) | ronin.mkv → Ronin.mkv | wird umbenannt',
    ]);
    expect(button('.rename-button').textContent).toContain('2 umbenennen');
  });

  it('switches to English at runtime', async () => {
    await load();
    await preview();

    TestBed.inject(TranslocoService).setActiveLang('en');
    await fixture.whenStable();

    expect(element().querySelector('.summary')!.textContent).toContain('2 to rename');
    expect(element().querySelector('.count-conflict')!.textContent).toContain('1 conflict');
    expect(rows()[0]).toContain('| conflict');
    expect(rows()[1]).toContain('| will be renamed');
    expect(button('.rename-button').textContent).toContain('Rename 2');
  });

  it('sorts the preview by a column header', async () => {
    await load();
    await preview();

    element()
      .querySelector<HTMLElement>('th[mat-sort-header="title"] .mat-sort-header-container')!
      .click();
    await fixture.whenStable();

    expect(rows()[0]).toContain('Ronin');
  });

  it('shows unchanged movies on request', async () => {
    await load();
    await preview();

    element().querySelector<HTMLInputElement>('.only-changes input')!.click();
    await fixture.whenStable();

    expect(rows()).toHaveLength(4);
  });

  it('says when all movies are named right already', async () => {
    await load();

    await preview([plans[0]]);

    expect(rows()).toEqual(['Der Film heißt schon so.']);
    expect(button('.rename-button').disabled).toBe(true);
  });

  it('renames only the selected movies and previews again when the job is done', async () => {
    await load();
    await preview();
    element().querySelector<HTMLInputElement>('.plans tbody tr input')!.click();
    await fixture.whenStable();

    button('.rename-button').click();
    const request = httpTesting.expectOne({
      method: 'POST',
      url: '/api/v1/sources/7/movie-rename',
    });
    expect(request.request.body).toEqual({
      patterns: { folder: '{title}[ ({year})]', file: '{title}' },
      movieIds: [3],
    });
    const job = {
      id: 'j',
      key: 'movie-rename:7',
      title: 'Filme umbenennen „Filme“',
      status: 'RUNNING',
    } as Job;
    request.flush(job);
    expect(updated).toEqual([job]);

    completed.next({
      ...job,
      status: 'SUCCEEDED',
      result: { sourceId: 7, renamed: 1, unchanged: 1, skipped: 1, failed: 0, problems: [] },
    } as Job);
    httpTesting
      .expectOne({ method: 'POST', url: '/api/v1/sources/7/movie-rename/preview' })
      .flush(plans);

    expect(snackBarMessages).toEqual([
      'Filme umbenennen „Filme“ abgeschlossen: 1 umbenannt, 1 unverändert, 1 übersprungen.',
    ]);
  });

  it('selects all or none', async () => {
    await load();
    await preview();

    button('.select-none').click();
    await fixture.whenStable();
    expect(button('.rename-button').disabled).toBe(true);
    button('.select-all').click();
    await fixture.whenStable();
    expect(button('.rename-button').textContent).toContain('2 umbenennen');
  });

  it('shows a running job', async () => {
    running = {
      id: 'j',
      key: 'movie-rename:7',
      title: 'x',
      status: 'RUNNING',
      message: 'Heat',
    } as Job;
    await load();

    expect(element().querySelector('.job-progress')!.textContent).toContain('Heat');
    expect(button('.preview-button').disabled).toBe(true);
  });

  it('reports invalid patterns', async () => {
    await load();

    button('.preview-button').click();
    httpTesting
      .expectOne({ method: 'POST', url: '/api/v1/sources/7/movie-rename/preview' })
      .flush({ detail: 'Ordnermuster: kaputt' }, { status: 422, statusText: 'Unprocessable' });

    expect(snackBarMessages).toEqual(['Ordnermuster: kaputt']);
  });
});
