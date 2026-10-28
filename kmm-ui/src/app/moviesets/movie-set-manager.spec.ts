import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';

import { Job } from '../jobs/job';
import { JobStore } from '../jobs/job-store';
import { CONFIRM } from '../shared/confirm';
import { flushWhenRequested } from '../shared/testing';
import { MovieSetDetails, MovieSetSummary, movieSetJobSummary } from './movie-set';
import { MovieSetManager } from './movie-set-manager';
import { MOVIE_SETS_URL } from './movie-set.service';

describe('MovieSetManager', () => {
  const sets: MovieSetSummary[] = [
    {
      id: 1,
      name: 'Matrix Filmreihe',
      overview: 'Neo',
      tmdbCollectionId: '2344',
      sortMethod: 'YEAR',
      movies: 2,
    },
    {
      id: 2,
      name: 'Miss Marple Filmreihe',
      overview: null,
      tmdbCollectionId: null,
      sortMethod: 'TITLE',
      movies: 0,
    },
  ];
  const matrix: MovieSetDetails = {
    id: 1,
    data: {
      name: 'Matrix Filmreihe',
      overview: 'Neo',
      tmdbCollectionId: '2344',
      sortMethod: 'YEAR',
    },
    movies: [
      { id: 5, title: 'Matrix', year: 1999 },
      { id: 6, title: 'Matrix Reloaded', year: 2003 },
    ],
  };

  let fixture: ComponentFixture<MovieSetManager>;
  let httpTesting: HttpTestingController;
  let snackBarMessages: string[];
  let confirmAnswer: boolean;
  const completed = new Subject<Job>();
  const updated: Job[] = [];
  let running: Job | undefined;

  beforeEach(() => {
    snackBarMessages = [];
    confirmAnswer = true;
    updated.length = 0;
    running = undefined;
    TestBed.configureTestingModule({
      imports: [MovieSetManager],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: MatSnackBar,
          useValue: { open: (message: string) => snackBarMessages.push(message) },
        },
        { provide: CONFIRM, useValue: () => confirmAnswer },
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
    fixture = TestBed.createComponent(MovieSetManager);
  });

  afterEach(() => httpTesting.verify());

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function button(selector: string): HTMLButtonElement {
    return element().querySelector<HTMLButtonElement>(selector)!;
  }

  function texts(selector: string): string[] {
    return [...element().querySelectorAll(selector)].map((node) =>
      node.textContent!.replace(/\s+/g, ' ').trim(),
    );
  }

  function input(name: string): HTMLInputElement {
    return element().querySelector<HTMLInputElement>(`[name="${name}"]`)!;
  }

  async function type(name: string, value: string): Promise<void> {
    input(name).value = value;
    input(name).dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function load(): Promise<void> {
    fixture.detectChanges();
    httpTesting.expectOne(MOVIE_SETS_URL).flush(sets);
    await fixture.whenStable();
  }

  async function selectMatrix(): Promise<void> {
    element().querySelector<HTMLButtonElement>('.set')!.click();
    await flushWhenRequested(fixture, httpTesting, `${MOVIE_SETS_URL}/1`, matrix);
    await fixture.whenStable();
  }

  function job(title: string): Job {
    return { id: 'j', key: 'moviesets', title, status: 'RUNNING' } as Job;
  }

  it('lists and filters sets and shows the selected one', async () => {
    await load();
    expect(texts('.set')).toEqual(['Matrix Filmreihe 2', 'Miss Marple Filmreihe 0']);

    await type('filter', 'marple');
    expect(texts('.set')).toEqual(['Miss Marple Filmreihe 0']);
    await type('filter', '');

    await selectMatrix();
    expect(input('name').value).toBe('Matrix Filmreihe');
    expect(input('tmdbCollectionId').value).toBe('2344');
    expect(texts('.members li')).toEqual(['Matrix (1999)', 'Matrix Reloaded (2003)']);
  });

  it('creates a set and selects it', async () => {
    await load();
    await type('newSet', ' Bud Spencer ');
    element().querySelector<HTMLFormElement>('.new-set')!.dispatchEvent(new Event('submit'));

    const request = httpTesting.expectOne({ method: 'POST', url: MOVIE_SETS_URL });
    expect(request.request.body).toEqual({
      name: 'Bud Spencer',
      overview: null,
      tmdbCollectionId: null,
      sortMethod: 'YEAR',
    });
    request.flush({ ...matrix, id: 3, data: { ...matrix.data, name: 'Bud Spencer' }, movies: [] });
    await flushWhenRequested(fixture, httpTesting, MOVIE_SETS_URL, sets);
    await flushWhenRequested(fixture, httpTesting, `${MOVIE_SETS_URL}/3`, {
      ...matrix,
      id: 3,
      movies: [],
    });

    expect(snackBarMessages).toEqual(['Filmset „Bud Spencer“ angelegt.']);
  });

  it('saves changes and reloads after the job', async () => {
    await load();
    await selectMatrix();
    expect(button('.save-button').disabled).toBe(true);

    await type('name', 'Matrix Trilogie');
    await type('tmdbCollectionId', ' ');
    button('.save-button').click();
    const request = httpTesting.expectOne({ method: 'PUT', url: `${MOVIE_SETS_URL}/1` });
    expect(request.request.body).toEqual({
      name: 'Matrix Trilogie',
      overview: 'Neo',
      tmdbCollectionId: null,
      sortMethod: 'YEAR',
    });
    request.flush(job('Filmset speichern'));

    completed.next({
      ...job('Filmset speichern'),
      status: 'SUCCEEDED',
      result: { changed: 2, unchanged: 0, failed: 0, failures: [] },
    } as Job);
    await flushWhenRequested(fixture, httpTesting, MOVIE_SETS_URL, sets);
    await flushWhenRequested(fixture, httpTesting, `${MOVIE_SETS_URL}/1`, matrix);
    expect(snackBarMessages).toEqual(['Filmset speichern: 2 Filme geändert.']);
  });

  it('removes marked movies and adds found ones', async () => {
    await load();
    await selectMatrix();

    element().querySelector<HTMLInputElement>('.members input')!.click();
    await fixture.whenStable();
    button('.remove-button').click();
    const remove = httpTesting.expectOne({
      method: 'POST',
      url: `${MOVIE_SETS_URL}/1/movies/remove`,
    });
    expect(remove.request.body).toEqual({ movieIds: [5] });
    remove.flush(job('Filme entfernen'));

    await type('search', 'Animatrix');
    button('.search-button').click();
    httpTesting
      .expectOne(
        (request) =>
          request.url === '/api/v1/movies' && request.params.get('search') === 'Animatrix',
      )
      .flush({
        content: [{ id: 7, title: 'Animatrix', year: 2003 }],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      });
    await fixture.whenStable();
    expect(texts('.candidates li')).toEqual(['Animatrix (2003)']);
    element().querySelector<HTMLInputElement>('.candidates input')!.click();
    await fixture.whenStable();
    button('.add-button').click();

    const add = httpTesting.expectOne({ method: 'POST', url: `${MOVIE_SETS_URL}/1/movies` });
    expect(add.request.body).toEqual({ movieIds: [7] });
    add.flush(job('Filme hinzufügen'));
    expect(updated).toHaveLength(2);
  });

  it('deletes a set only after confirmation and reports problems', async () => {
    await load();
    await selectMatrix();

    confirmAnswer = false;
    button('.delete-button').click();
    confirmAnswer = true;
    button('.delete-button').click();
    httpTesting
      .expectOne({ method: 'DELETE', url: `${MOVIE_SETS_URL}/1` })
      .flush({ detail: 'Es läuft schon ein Job.' }, { status: 409, statusText: 'Conflict' });
    button('.search-button').click();

    expect(snackBarMessages).toEqual(['Es läuft schon ein Job.']);
    expect(texts('.candidates li')).toEqual([]);
  });

  it('summarizes jobs and shows a running one', async () => {
    running = { ...job('Filmset löschen'), message: 'Film 5' } as Job;
    await load();
    expect(texts('.job-progress')).toEqual(['Filmset löschen – Film 5']);

    const done = { ...job('Filmset löschen'), status: 'SUCCEEDED' } as Job;
    expect(
      movieSetJobSummary({
        ...done,
        result: { changed: 1, unchanged: 0, failed: 1, failures: ['Film 5: x'] },
      }),
    ).toBe('Filmset löschen: 1 Filme geändert, 1 Fehler: Film 5: x.');
    expect(movieSetJobSummary({ ...done, status: 'CANCELLED' })).toBe(
      'Filmset löschen abgebrochen.',
    );
    expect(movieSetJobSummary({ ...done, status: 'FAILED', message: null })).toBe(
      'Filmset löschen fehlgeschlagen: Unbekannter Fehler',
    );
  });
});
