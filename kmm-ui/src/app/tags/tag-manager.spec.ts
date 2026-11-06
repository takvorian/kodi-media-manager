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
import { CONFIRM } from '../shared/confirm';
import { flushWhenRequested } from '../shared/testing';
import { TagSummary, TaggedItems, tagJobSummary } from './tag';
import { TagManager } from './tag-manager';
import { TAGS_URL } from './tag.service';
import { literal } from '../i18n/localized-text';

describe('TagManager', () => {
  const tags: TagSummary[] = [
    { name: 'Krimi', movies: 1, shows: 1 },
    { name: 'Spencer', movies: 2, shows: 0 },
  ];
  const krimi: TaggedItems = {
    tag: 'Krimi',
    movies: [{ id: 5, title: 'Heat' }],
    shows: [{ id: 7, title: 'Monk' }],
  };

  let fixture: ComponentFixture<TagManager>;
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
      imports: [TagManager],
      providers: [
        provideHttpClient(),
        provideI18nTesting(),
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
    fixture = TestBed.createComponent(TagManager);
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

  async function type(name: string, value: string): Promise<void> {
    const input = element().querySelector<HTMLInputElement>(`[name="${name}"]`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function load(): Promise<void> {
    fixture.detectChanges();
    httpTesting.expectOne(TAGS_URL).flush(tags);
    await fixture.whenStable();
  }

  async function selectKrimi(): Promise<void> {
    element().querySelector<HTMLButtonElement>('.tag')!.click();
    fixture.detectChanges();
    httpTesting.expectOne((request) => request.url === `${TAGS_URL}/items`).flush(krimi);
    await fixture.whenStable();
  }

  function job(title: string): Job {
    return { id: 'j', key: 'tags', title: literal(title), status: 'RUNNING' } as Job;
  }

  it('switches to English at runtime', async () => {
    await load();
    await selectKrimi();

    TestBed.inject(TranslocoService).setActiveLang('en');
    await fixture.whenStable();

    expect(texts('.tag')).toEqual(['Krimi 1 movie · 1 TV show', 'Spencer 2 movies · 0 TV shows']);
    expect(texts('h3')).toEqual(['Movies (1)', 'TV shows (1)', 'Assign']);
    expect(texts('.rename-button')).toEqual(['Rename']);
    expect(tagJobSummary({ ...job('Delete tag'), status: 'FAILED', message: null })).toBe(
      'Delete tag failed: Unknown error',
    );
  });

  it('lists the tags and shows the items of the selected one', async () => {
    await load();
    expect(texts('.tag')).toEqual(['Krimi 1 Film · 1 Serie', 'Spencer 2 Filme · 0 Serien']);

    await selectKrimi();

    expect(texts('.selected-tag')).toEqual(['Krimi']);
    expect(texts('.movies li')).toEqual(['Heat']);
    expect(texts('.shows li')).toEqual(['Monk']);
  });

  it('renames and deletes a tag and reloads after the job', async () => {
    await load();
    await selectKrimi();

    await type('newName', 'Thriller');
    button('.rename-button').click();
    const rename = httpTesting.expectOne({ method: 'POST', url: `${TAGS_URL}/rename` });
    expect(rename.request.body).toEqual({ tag: 'Krimi', newName: 'Thriller' });
    rename.flush(job('Tag umbenennen'));
    fixture.detectChanges();
    httpTesting
      .expectOne((request) => request.url === `${TAGS_URL}/items`)
      .flush({ ...krimi, tag: 'Thriller' });
    await fixture.whenStable();

    completed.next({
      ...job('Tag umbenennen'),
      status: 'SUCCEEDED',
      result: { changed: 2, unchanged: 0, failed: 0, failures: [] },
    } as Job);
    await flushWhenRequested(fixture, httpTesting, TAGS_URL, tags);
    await flushWhenRequested(fixture, httpTesting, `${TAGS_URL}/items?tag=Thriller`, krimi);
    expect(snackBarMessages).toEqual(['Tag umbenennen: 2 geändert, 0 unverändert.']);

    confirmAnswer = false;
    button('.delete-button').click();
    confirmAnswer = true;
    button('.delete-button').click();
    httpTesting.expectOne({ method: 'POST', url: `${TAGS_URL}/delete` }).flush(job('Tag löschen'));
    expect(updated).toHaveLength(2);
  });

  it('removes marked items', async () => {
    await load();
    await selectKrimi();

    element().querySelector<HTMLInputElement>('.shows input')!.click();
    await fixture.whenStable();
    button('.remove-button').click();

    const request = httpTesting.expectOne({ method: 'POST', url: `${TAGS_URL}/unassign` });
    expect(request.request.body).toEqual({ tag: 'Krimi', movieIds: [], showIds: [7] });
    request.flush(job('Tag entfernen'));
  });

  it('assigns a new tag to found movies and shows', async () => {
    await load();
    await type('newTag', ' Karl May ');
    element().querySelector<HTMLFormElement>('.new-tag')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    httpTesting
      .expectOne((request) => request.url === `${TAGS_URL}/items`)
      .flush({ tag: 'Karl May', movies: [], shows: [] });
    await fixture.whenStable();
    expect(texts('.selected-tag')).toEqual(['Karl May']);

    await type('search', 'Winnetou');
    button('.search-button').click();
    httpTesting
      .expectOne(
        (request) =>
          request.url === '/api/v1/movies' && request.params.get('search') === 'Winnetou',
      )
      .flush({
        content: [{ id: 3, title: 'Winnetou', year: 1963 }],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      });
    httpTesting
      .expectOne(
        (request) =>
          request.url === '/api/v1/tvshows' && request.params.get('search') === 'Winnetou',
      )
      .flush({
        content: [{ id: 9, title: 'Winnetous Rückkehr' }],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      });
    await fixture.whenStable();
    expect(texts('.candidates li')).toEqual(['Winnetou (1963)', 'Winnetous Rückkehr (Serie)']);

    element()
      .querySelectorAll<HTMLInputElement>('.candidates input')
      .forEach((checkbox) => checkbox.click());
    await fixture.whenStable();
    button('.assign-button').click();

    const request = httpTesting.expectOne({ method: 'POST', url: `${TAGS_URL}/assign` });
    expect(request.request.body).toEqual({ tag: 'Karl May', movieIds: [3], showIds: [9] });
    request.flush(job('Tag zuweisen'));
  });

  it('reports problems and an empty search', async () => {
    await load();
    await selectKrimi();

    button('.search-button').click();
    expect(texts('.candidates li')).toEqual([]);
    button('.remove-button').click();
    await type('newName', 'X');
    button('.rename-button').click();
    httpTesting
      .expectOne({ method: 'POST', url: `${TAGS_URL}/rename` })
      .flush({ detail: 'Es läuft schon ein Job.' }, { status: 409, statusText: 'Conflict' });

    expect(snackBarMessages).toEqual(['Es läuft schon ein Job.']);
  });

  it('summarizes jobs', () => {
    const done = {
      id: 'j',
      key: 'tags',
      title: literal('Tag löschen'),
      status: 'SUCCEEDED',
    } as Job;

    expect(
      tagJobSummary({
        ...done,
        result: { changed: 1, unchanged: 0, failed: 1, failures: ['Film 3: kaputt'] },
      }),
    ).toBe('Tag löschen: 1 geändert, 0 unverändert, 1 Fehler: Film 3: kaputt.');
    expect(tagJobSummary({ ...done, status: 'CANCELLED' })).toBe('Tag löschen abgebrochen.');
    expect(tagJobSummary({ ...done, status: 'FAILED', message: null })).toBe(
      'Tag löschen fehlgeschlagen: Unbekannter Fehler',
    );
  });

  it('shows a running job', async () => {
    running = { ...job('Tag zuweisen'), message: literal('Film 3') } as Job;
    await load();

    expect(texts('.job-progress')).toEqual(['Tag zuweisen – Film 3']);
  });
});
