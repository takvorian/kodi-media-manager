import { By } from '@angular/platform-browser';
import { provideI18nTesting } from '../i18n/testing';
import { LanguageSelect } from '../shared/language-select';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';

import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoService } from '@jsverse/transloco';

import { FILTERS_URL, SavedFilter } from '../filters/saved-filter.service';
import { Job } from '../jobs/job';
import { JobStore } from '../jobs/job-store';
import { CONFIRM } from '../shared/confirm';
import { flushWhenRequested } from '../shared/testing';
import { MoviePage, MovieSummary } from './movie';
import { localDateTime } from '../shared/date-time';
import { DEFAULT_PAGE_SIZE, MovieList, SEARCH_DEBOUNCE_MS } from './movie-list';
import { MOVIES_URL } from './movie.service';

const DEBOUNCE_SAFETY_MARGIN_MS = 50;

describe('MovieList', () => {
  const heat: MovieSummary = {
    id: 1,
    sourceId: 1,
    title: 'Heat',
    year: 1995,
    filePath: '/media/filme/Heat.mkv',
    discFormat: 'FILE',
    partCount: 1,
    addedAt: '2026-10-25T10:00:00Z',
  };
  const ronin: MovieSummary = {
    ...heat,
    id: 2,
    title: 'Ronin',
    year: null,
    discFormat: 'DVD',
    partCount: 2,
  };

  const unseen: SavedFilter = {
    id: 7,
    mediaType: 'MOVIE',
    name: 'tiktak_unviewed',
    definition: {
      matchAll: true,
      conditions: [{ field: 'WATCHED', operator: 'IS', value: 'false' }],
    },
  };

  let fixture: ComponentFixture<MovieList>;
  let httpTesting: HttpTestingController;
  let snackBarMessages: string[];
  const completed = new Subject<Job>();
  const updated: Job[] = [];
  const running = signal<Job | undefined>(undefined);
  const history = signal<Job[]>([]);
  const scrapeJob = {
    id: 's',
    key: 'movies-scrape',
    title: '2 Filme scrapen',
    status: 'RUNNING',
    done: 1,
    total: 2,
    message: 'Heat',
  } as Job;
  const finishedScrape = {
    ...scrapeJob,
    status: 'SUCCEEDED',
    result: {
      movies: 2,
      scraped: 1,
      unmatched: 1,
      imagesSaved: 3,
      unmatchedEntries: [{ type: 'MOVIE', id: 2, label: 'Unklar' }],
      failures: [],
      warnings: [],
    },
  } as Job;

  beforeEach(() => {
    running.set(undefined);
    history.set([]);
    snackBarMessages = [];
    updated.length = 0;
    TestBed.configureTestingModule({
      imports: [MovieList],
      providers: [
        provideHttpClient(),
        provideI18nTesting(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: MatSnackBar,
          useValue: { open: (message: string) => snackBarMessages.push(message) },
        },
        { provide: CONFIRM, useValue: () => true },
        {
          provide: JobStore,
          useValue: {
            connect: () => undefined,
            completed,
            jobs: () => history(),
            runningJob: () => running(),
            cancel: () => of({ ...scrapeJob, status: 'CANCELLED' }),
            update: (job: Job) => updated.push(job),
          },
        },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MovieList);
  });

  afterEach(() => httpTesting.verify());

  function page(content: MovieSummary[], totalElements = content.length): MoviePage {
    return { content, page: 0, size: DEFAULT_PAGE_SIZE, totalElements, totalPages: 1 };
  }

  function expectMovies(): TestRequest {
    return httpTesting.expectOne((request) => request.url === `${MOVIES_URL}/query`);
  }

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  async function respond(moviePage: MoviePage): Promise<TestRequest> {
    fixture.detectChanges();
    httpTesting
      .match((request) => request.url === FILTERS_URL)
      .forEach((request) => request.flush([unseen]));
    const request = expectMovies();
    request.flush(moviePage);
    await fixture.whenStable();
    return request;
  }

  /** Types a search term and lets the debounce time pass. */
  async function typeSearch(term: string): Promise<void> {
    const input = element().querySelector<HTMLInputElement>('input[name="search"]')!;
    input.value = term;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await new Promise((resolve) =>
      setTimeout(resolve, SEARCH_DEBOUNCE_MS + DEBOUNCE_SAFETY_MARGIN_MS),
    );
  }

  it('loads the first page sorted by title', async () => {
    const request = await respond(page([heat, ronin]));

    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe(String(DEFAULT_PAGE_SIZE));
    expect(
      Array.from(element().querySelectorAll('.movie-title')).map((cell) => cell.textContent),
    ).toEqual(['Heat', 'Ronin']);
  });

  it('sorts by a column header and starts again at the first page', async () => {
    await respond(page([heat, ronin]));

    element()
      .querySelector<HTMLElement>('th.mat-column-addedAt .mat-sort-header-container')!
      .click();
    fixture.detectChanges();
    const request = httpTesting.expectOne((candidate) => candidate.url === `${MOVIES_URL}/query`);

    expect(request.request.params.getAll('sort')).toEqual(['addedAt,desc', 'title,asc,ignorecase']);
    expect(request.request.params.get('page')).toBe('0');
    request.flush(page([ronin, heat]));
  });

  it('shows the paginator above and below the list', async () => {
    await respond(page([heat, ronin]));

    expect(element().querySelectorAll('mat-paginator')).toHaveLength(2);
  });

  it('shows when a movie was added in the language of the browser', async () => {
    await respond(page([heat]));

    expect(element().querySelector('.added-at')?.textContent?.trim()).toBe(
      localDateTime(heat.addedAt),
    );
  });

  it('shows year, format and number of parts', async () => {
    await respond(page([ronin]));

    const cells = Array.from(element().querySelectorAll('td')).map((cell) =>
      cell.textContent?.replace(/\s+/g, ' ').trim(),
    );
    expect(cells).toContain('–');
    expect(cells).toContain('DVD (2 Teile)');
  });

  it('switches to English at runtime', async () => {
    await respond(page([ronin]));

    TestBed.inject(TranslocoService).setActiveLang('en');
    await fixture.whenStable();

    const text = (selector: string) =>
      Array.from(element().querySelectorAll(selector)).map((cell) =>
        cell.textContent?.replace(/\s+/g, ' ').trim(),
      );
    expect(text('h2')).toEqual(['Movies']);
    expect(text('th')).toEqual(['', 'Title', 'Year', 'Format', 'Added', 'File']);
    expect(text('td')).toContain('DVD (2 parts)');
    expect(text('.selection > span')).toEqual(['0 selected']);
    expect(text('.edit-filter-button')).toEqual(['Edit filter']);
  });

  it('points to the sources page when the library is empty', async () => {
    await respond(page([]));

    expect(element().querySelector('.empty a')?.getAttribute('href')).toBe('/sources');
  });

  it('shows an error when the movies cannot be loaded', async () => {
    fixture.detectChanges();
    httpTesting.match(FILTERS_URL + '?mediaType=MOVIE').forEach((request) => request.flush([]));
    expectMovies().flush('boom', { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(element().querySelector('.error')).not.toBeNull();
  });

  it('searches after a pause in typing and starts at the first page', async () => {
    await respond(page([heat, ronin]));

    await typeSearch('ron');
    const request = await respond(page([ronin]));

    expect(request.request.body.search).toBe('ron');
    expect(request.request.params.get('page')).toBe('0');
    expect(element().querySelectorAll('.movie-title')).toHaveLength(1);
  });

  it('shows a hint when the search finds nothing', async () => {
    await respond(page([heat]));

    await typeSearch('xyz');
    await respond(page([]));

    expect(element().querySelector('.empty')?.textContent).toContain('Keine Filme gefunden');
  });

  it('applies predefined and saved filters', async () => {
    await respond(page([heat, ronin]));
    const list = fixture.componentInstance as unknown as { select(selection: string): void };

    list.select('predefined:3');
    const predefined = await respond(page([ronin]));
    expect(predefined.request.body.conditions).toEqual([
      { field: 'RESOLUTION', operator: 'AT_LEAST', value: '2160' },
    ]);

    list.select('saved:7');
    const saved = await respond(page([ronin]));
    expect(saved.request.body.conditions).toEqual(unseen.definition.conditions);
    expect(element().querySelector('.empty')).toBeNull();
  });

  it('edits conditions, skips incomplete ones and saves the filter under a name', async () => {
    await respond(page([heat, ronin]));
    element().querySelector<HTMLButtonElement>('.edit-filter-button')!.click();
    fixture.detectChanges();
    httpTesting.expectOne(`${MOVIES_URL}/filter-values`).flush({
      genres: ['Krimi'],
      tags: [],
      countries: [],
      studios: [],
      sets: [],
      mpaa: [],
      editions: [],
      videoSources: [],
      resolutions: [1080],
      videoCodecs: [],
      hdrTypes: [],
      audioCodecs: [],
      audioLanguages: [],
      subtitleLanguages: [],
    });
    httpTesting.expectOne('/api/v1/sources').flush([]);
    await fixture.whenStable();

    element().querySelector<HTMLButtonElement>('.add-condition')!.click();
    await fixture.whenStable();
    httpTesting.expectNone((request) => request.url === `${MOVIES_URL}/query`);

    const list = fixture.componentInstance as unknown as {
      onFilterChange(filter: unknown): void;
      filterName: { set(name: string): void };
    };
    list.onFilterChange({
      matchAll: true,
      conditions: [{ field: 'GENRE', operator: 'IS', value: 'Krimi' }],
    });
    const filtered = await respond(page([heat]));
    expect(filtered.request.body.conditions).toEqual([
      { field: 'GENRE', operator: 'IS', value: 'Krimi' },
    ]);

    list.filterName.set('Krimis');
    fixture.detectChanges();
    element().querySelector<HTMLButtonElement>('.save-filter-button')!.click();
    const save = httpTesting.expectOne({ method: 'POST', url: FILTERS_URL });
    expect(save.request.body).toEqual({
      mediaType: 'MOVIE',
      name: 'Krimis',
      definition: {
        matchAll: true,
        conditions: [{ field: 'GENRE', operator: 'IS', value: 'Krimi' }],
      },
    });
    save.flush({ ...unseen, id: 8, name: 'Krimis' });
    await flushWhenRequested(fixture, httpTesting, FILTERS_URL + '?mediaType=MOVIE', [
      unseen,
      { ...unseen, id: 8, name: 'Krimis' },
    ]);
    await fixture.whenStable();
    expect(snackBarMessages).toEqual(['Filter „Krimis“ gespeichert.']);

    element().querySelector<HTMLButtonElement>('.delete-filter-button')!.click();
    httpTesting.expectOne({ method: 'DELETE', url: `${FILTERS_URL}/8` }).flush(null);
    await flushWhenRequested(fixture, httpTesting, FILTERS_URL + '?mediaType=MOVIE', [unseen]);
    await respond(page([heat, ronin]));
    expect(snackBarMessages).toContain('Filter „Krimis“ gelöscht.');
  });

  it('chooses movies, edits them together and reloads after the job', async () => {
    await respond(page([heat, ronin]));
    element().querySelector<HTMLInputElement>('.choose input')!.click();
    fixture.detectChanges();
    expect(element().querySelector('.selection')?.textContent).toContain('1 ausgewählt');

    element().querySelector<HTMLButtonElement>('.choose-all-button')!.click();
    const ids = httpTesting.expectOne(`${MOVIES_URL}/query/ids`);
    expect(ids.request.body).toEqual({ matchAll: true, conditions: [], search: null });
    ids.flush([1, 2, 3]);
    fixture.detectChanges();
    expect(element().querySelector('.selection')?.textContent).toContain('3 ausgewählt');

    element().querySelector<HTMLButtonElement>('.batch-edit-button')!.click();
    fixture.detectChanges();
    const addGenres = element().querySelector<HTMLInputElement>('input[name="addGenres"]')!;
    addGenres.value = 'Klassiker, Krimi';
    addGenres.dispatchEvent(new Event('input'));
    element().querySelector<HTMLButtonElement>('.apply-batch-button')!.click();
    const batch = httpTesting.expectOne({ method: 'POST', url: `${MOVIES_URL}/batch` });
    expect(batch.request.body).toEqual({
      movieIds: [1, 2, 3],
      genres: { add: ['Klassiker', 'Krimi'], remove: [] },
      tags: { add: [], remove: [] },
      mpaa: { clear: false, value: null },
      edition: { clear: false, value: null },
      videoSource: { clear: false, value: null },
    });
    const job = {
      id: 'b',
      key: 'movies-batch',
      title: '3 Filme bearbeiten',
      status: 'RUNNING',
    } as Job;
    batch.flush(job);
    expect(updated).toEqual([job]);

    completed.next({
      ...job,
      status: 'SUCCEEDED',
      result: { changed: 3, unchanged: 0, failed: 0, failures: [] },
    });
    await respond(page([heat, ronin]));
    expect(snackBarMessages).toContain('3 Filme bearbeiten: 3 geändert, 0 unverändert.');
  });

  describe('scraping the selection', () => {
    function report(): HTMLElement | null {
      return element().querySelector<HTMLElement>('.scrape-report');
    }

    it('starts a job for exactly the chosen entries', async () => {
      await respond(page([heat, ronin]));
      element().querySelector<HTMLButtonElement>('.choose-all-button')!.click();
      httpTesting.expectOne(`${MOVIES_URL}/query/ids`).flush([1, 2]);
      fixture.detectChanges();

      element().querySelector<HTMLButtonElement>('.scrape-chosen-button')!.click();

      const start = httpTesting.expectOne({ method: 'POST', url: `${MOVIES_URL}/auto-scrape` });
      expect(start.request.body).toEqual({ movieIds: [1, 2] });
      start.flush(scrapeJob);
      expect(updated).toEqual([scrapeJob]);
    });

    it('offers no scraping without a selection', async () => {
      await respond(page([heat, ronin]));

      expect(element().querySelector('.scrape-chosen-button')).toBeNull();
    });

    it('shows progress and can cancel', async () => {
      running.set(scrapeJob);
      await respond(page([heat, ronin]));

      expect(element().querySelector('.scrape-message')?.textContent).toContain('1 von 2');
      element().querySelector<HTMLButtonElement>('.cancel-scrape-button')!.click();
      expect(updated.map((job) => job.status)).toEqual(['CANCELLED']);
    });

    it('keeps the report open and links what it could not match', async () => {
      await respond(page([heat, ronin]));

      completed.next(finishedScrape);
      await respond(page([heat, ronin]));
      fixture.detectChanges();

      expect(report()?.textContent).toContain(
        '2 Filme scrapen abgeschlossen: 2 Filme, 1 neu zugeordnet, 1 ohne Treffer, 3 Bilder gespeichert.',
      );
      expect(report()?.querySelector('.unmatched-entries a')?.getAttribute('href')).toBe(
        '/movies/2',
      );

      report()!.querySelector<HTMLButtonElement>('.close-report-button')!.click();
      fixture.detectChanges();
      expect(report()).toBeNull();
    });

    it('offers the report of a run that ended while on another page', async () => {
      history.set([finishedScrape]);
      await respond(page([heat, ronin]));
      expect(report()).toBeNull();

      element().querySelector<HTMLButtonElement>('.show-scrape-report-button')!.click();
      fixture.detectChanges();

      expect(report()).not.toBeNull();
      expect(element().querySelector('.show-scrape-report-button')).toBeNull();
    });
  });

  it('fetches the missing trailers of the chosen movies as a job', async () => {
    await respond(page([heat, ronin]));
    element().querySelector<HTMLInputElement>('.choose input')!.click();
    fixture.detectChanges();

    element().querySelector<HTMLButtonElement>('.fill-trailers-button')!.click();
    const fill = httpTesting.expectOne({ method: 'POST', url: `${MOVIES_URL}/trailers/fill` });
    expect(fill.request.body).toEqual({ movieIds: [heat.id] });
    const job = {
      id: 't',
      key: 'movies-batch',
      title: 'Fehlende Trailer für 1 Filme holen',
      status: 'RUNNING',
    } as Job;
    fill.flush(job);

    expect(updated).toEqual([job]);
  });

  it('changes the language of the chosen movies as a job', async () => {
    await respond(page([heat, ronin]));
    element().querySelector<HTMLInputElement>('.choose input')!.click();
    fixture.detectChanges();

    const select = fixture.debugElement.query(By.directive(LanguageSelect)).componentInstance;
    select.chosen.emit('fr-FR');

    const change = httpTesting.expectOne({ method: 'POST', url: `${MOVIES_URL}/language` });
    expect(change.request.body).toEqual({
      movieIds: [heat.id],
      language: 'fr-FR',
      refetchTexts: true,
    });
    const job = { id: 'l', key: 'movies-batch', title: 'Sprache', status: 'RUNNING' } as Job;
    change.flush(job);

    expect(updated).toEqual([job]);
  });

  it('refreshes the chosen movies in Kodi as a job', async () => {
    await respond(page([heat, ronin]));
    element().querySelector<HTMLInputElement>('.choose input')!.click();
    fixture.detectChanges();

    element().querySelector<HTMLButtonElement>('.refresh-kodi-button')!.click();
    const refresh = httpTesting.expectOne({ method: 'POST', url: '/api/v1/kodi/movies/refresh' });
    expect(refresh.request.body).toEqual({ movieIds: [heat.id] });
    const job = {
      id: 'k',
      key: 'movies-batch',
      title: '1 Film in Kodi aktualisieren',
      status: 'RUNNING',
    } as Job;
    refresh.flush(job);

    expect(updated).toEqual([job]);
  });

  it('refreshes the ratings of the chosen movies as a job', async () => {
    await respond(page([heat, ronin]));
    element().querySelector<HTMLInputElement>('.choose input')!.click();
    fixture.detectChanges();

    element().querySelector<HTMLButtonElement>('.refresh-ratings-button')!.click();
    const fill = httpTesting.expectOne({ method: 'POST', url: `${MOVIES_URL}/ratings/refresh` });
    expect(fill.request.body).toEqual({ movieIds: [heat.id] });
    const job = {
      id: 'r',
      key: 'movies-batch',
      title: 'Bewertungen aktualisieren',
      status: 'RUNNING',
    } as Job;
    fill.flush(job);

    expect(updated).toEqual([job]);
  });

  it('fetches the missing themes of the chosen movies as a job', async () => {
    await respond(page([heat, ronin]));
    element().querySelector<HTMLInputElement>('.choose input')!.click();
    fixture.detectChanges();

    element().querySelector<HTMLButtonElement>('.fill-themes-button')!.click();
    const fill = httpTesting.expectOne({ method: 'POST', url: `${MOVIES_URL}/themes/fill` });
    expect(fill.request.body).toEqual({ movieIds: [heat.id] });
    const job = {
      id: 't',
      key: 'movies-batch',
      title: 'Fehlende Titelmusik',
      status: 'RUNNING',
    } as Job;
    fill.flush(job);

    expect(updated).toEqual([job]);
  });

  it('asks for at least one change before editing together', async () => {
    await respond(page([heat]));
    element().querySelector<HTMLInputElement>('.choose input')!.click();
    fixture.detectChanges();
    element().querySelector<HTMLButtonElement>('.batch-edit-button')!.click();
    fixture.detectChanges();

    element().querySelector<HTMLButtonElement>('.apply-batch-button')!.click();
    fixture.detectChanges();

    expect(element().querySelector('.batch-editor .error')?.textContent).toContain(
      'mindestens eine',
    );
    element().querySelector<HTMLButtonElement>('.clear-chosen-button')!.click();
    fixture.detectChanges();
    expect(element().querySelector('.batch-editor')).toBeNull();
  });

  it('loads another page when paging', async () => {
    await respond(page([heat], 120));

    const nextButton = element().querySelector<HTMLButtonElement>(
      '.mat-mdc-paginator-navigation-next',
    )!;
    nextButton.click();
    const request = await respond(page([ronin], 120));

    expect(request.request.params.get('page')).toBe('1');
  });
});
