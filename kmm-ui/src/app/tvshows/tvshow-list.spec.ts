import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';

import { FILTERS_URL } from '../filters/saved-filter.service';
import { Job } from '../jobs/job';
import { JobStore } from '../jobs/job-store';
import { CONFIRM } from '../shared/confirm';
import { flushWhenRequested } from '../shared/testing';

import { TvShowPage, TvShowSummary } from './tvshow';
import { localDateTime } from '../shared/date-time';
import { DEFAULT_PAGE_SIZE, SEARCH_DEBOUNCE_MS, TvShowList } from './tvshow-list';
import { TVSHOWS_URL } from './tvshow.service';

const DEBOUNCE_SAFETY_MARGIN_MS = 50;

describe('TvShowList', () => {
  const alf: TvShowSummary = {
    id: 1,
    sourceId: 2,
    title: 'ALF',
    year: 1986,
    directoryPath: '/media/serien/ALF (1986)',
    episodeCount: 99,
    addedAt: '2026-10-26T10:00:00Z',
  };

  let fixture: ComponentFixture<TvShowList>;
  let httpTesting: HttpTestingController;
  let snackBarMessages: string[];
  const completed = new Subject<Job>();
  const updated: Job[] = [];
  const running = signal<Job | undefined>(undefined);
  const history = signal<Job[]>([]);
  const scrapeJob = {
    id: 's',
    key: 'tvshows-scrape',
    title: '2 Serien scrapen',
    status: 'RUNNING',
    done: 1,
    total: 2,
    message: 'Heat',
  } as Job;
  const finishedScrape = {
    ...scrapeJob,
    status: 'SUCCEEDED',
    result: {
      shows: 2,
      episodesUpdated: 1,
      scraped: 1,
      unmatched: 1,
      imagesSaved: 3,
      unmatchedEntries: [{ type: 'TV_SHOW', id: 2, label: 'Unklar' }],
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
      imports: [TvShowList],
      providers: [
        provideHttpClient(),
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
    fixture = TestBed.createComponent(TvShowList);
  });

  afterEach(() => httpTesting.verify());

  function page(content: TvShowSummary[], totalElements = content.length): TvShowPage {
    return { content, page: 0, size: DEFAULT_PAGE_SIZE, totalElements, totalPages: 1 };
  }

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function expectShows(): TestRequest {
    return httpTesting.expectOne((req) => req.url === `${TVSHOWS_URL}/query`);
  }

  async function respond(showPage: TvShowPage): Promise<TestRequest> {
    fixture.detectChanges();
    httpTesting.match((req) => req.url === FILTERS_URL).forEach((req) => req.flush([]));
    const request = expectShows();
    request.flush(showPage);
    await fixture.whenStable();
    return request;
  }

  it('sorts by a column header and starts again at the first page', async () => {
    await respond(page([alf]));

    element()
      .querySelector<HTMLElement>('th.mat-column-episodeCount .mat-sort-header-container')!
      .click();
    fixture.detectChanges();
    const request = httpTesting.expectOne((candidate) => candidate.url === `${TVSHOWS_URL}/query`);

    expect(request.request.params.getAll('sort')).toEqual([
      'episodeCount,asc',
      'title,asc,ignorecase',
    ]);
    expect(request.request.params.get('page')).toBe('0');
    request.flush(page([alf]));
  });

  it('shows the paginator above and below the list', async () => {
    await respond(page([alf]));

    expect(element().querySelectorAll('mat-paginator')).toHaveLength(2);
  });

  it('shows when a show was added in the language of the browser', async () => {
    await respond(page([alf]));

    expect(element().querySelector('.added-at')?.textContent?.trim()).toBe(
      localDateTime(alf.addedAt),
    );
  });

  it('lists the shows with year and number of episodes', async () => {
    const request = await respond(
      page([alf, { ...alf, id: 2, title: 'Dark', year: null, episodeCount: 26 }]),
    );

    expect(request.request.params.get('sort')).toBe('title,asc,ignorecase');
    expect(
      Array.from(element().querySelectorAll('.tvshow-title a')).map((link) =>
        link.getAttribute('href'),
      ),
    ).toEqual(['/tvshows/1', '/tvshows/2']);
    expect(
      Array.from(element().querySelectorAll('.episode-count')).map((cell) => cell.textContent),
    ).toEqual(['99', '26']);
    expect(element().textContent).toContain('–');
  });

  it('points to the sources page when there are no shows', async () => {
    await respond(page([]));

    expect(element().querySelector('.empty a')?.getAttribute('href')).toBe('/sources');
  });

  it('searches after a pause and pages through the results', async () => {
    await respond(page([alf], 120));

    const input = element().querySelector<HTMLInputElement>('input[name="search"]')!;
    input.value = 'alf';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await new Promise((resolve) =>
      setTimeout(resolve, SEARCH_DEBOUNCE_MS + DEBOUNCE_SAFETY_MARGIN_MS),
    );
    const searched = await respond(page([]));
    expect(searched.request.body.search).toBe('alf');
    expect(element().querySelector('.empty')?.textContent).toContain('Keine Serien gefunden');
  });

  it('filters by predefined filters and edits and saves own conditions', async () => {
    await respond(page([alf]));
    const list = fixture.componentInstance as unknown as {
      select(selection: string): void;
      onFilterChange(filter: unknown): void;
      filterName: { set(name: string): void };
    };

    list.select('predefined:0');
    const unwatched = await respond(page([alf]));
    expect(unwatched.request.body.conditions).toEqual([
      { field: 'UNWATCHED_EPISODES', operator: 'IS', value: 'true' },
    ]);

    element().querySelector<HTMLButtonElement>('.edit-filter-button')!.click();
    fixture.detectChanges();
    httpTesting.expectOne(`${TVSHOWS_URL}/filter-values`).flush({
      genres: ['Comedy'],
      tags: [],
      studios: [],
      statuses: ['Ended'],
      mpaa: [],
      videoSources: [],
      resolutions: [576],
      videoCodecs: [],
      audioCodecs: [],
      audioLanguages: [],
      subtitleLanguages: [],
    });
    httpTesting.expectOne('/api/v1/sources').flush([]);
    await fixture.whenStable();
    expect(element().querySelector('app-filter-editor')).not.toBeNull();

    list.onFilterChange({
      matchAll: true,
      conditions: [{ field: 'EPISODE_RESOLUTION', operator: 'AT_MOST', value: '576' }],
    });
    const filtered = await respond(page([alf]));
    expect(filtered.request.body.conditions).toEqual([
      { field: 'EPISODE_RESOLUTION', operator: 'AT_MOST', value: '576' },
    ]);

    list.filterName.set('SD-Serien');
    fixture.detectChanges();
    element().querySelector<HTMLButtonElement>('.save-filter-button')!.click();
    const save = httpTesting.expectOne({ method: 'POST', url: FILTERS_URL });
    expect(save.request.body.mediaType).toBe('TV_SHOW');
    expect(save.request.body.name).toBe('SD-Serien');
    save.flush({
      id: 3,
      mediaType: 'TV_SHOW',
      name: 'SD-Serien',
      definition: save.request.body.definition,
    });
    await flushWhenRequested(fixture, httpTesting, FILTERS_URL + '?mediaType=TV_SHOW', []);
    expect(snackBarMessages).toEqual(['Filter „SD-Serien“ gespeichert.']);
  });

  it('chooses filtered shows and edits them together', async () => {
    await respond(page([alf]));
    element().querySelector<HTMLButtonElement>('.choose-all-button')!.click();
    httpTesting.expectOne(`${TVSHOWS_URL}/query/ids`).flush([1, 4]);
    fixture.detectChanges();
    expect(element().querySelector('.selection')?.textContent).toContain('2 ausgewählt');

    element().querySelector<HTMLButtonElement>('.batch-edit-button')!.click();
    fixture.detectChanges();
    expect(element().querySelector('.batch-editor')?.textContent).toContain('2 Serien ausgewählt');
    const addTags = element().querySelector<HTMLInputElement>('input[name="addTags"]')!;
    addTags.value = 'Kult';
    addTags.dispatchEvent(new Event('input'));
    element().querySelector<HTMLButtonElement>('.apply-batch-button')!.click();

    const batch = httpTesting.expectOne({ method: 'POST', url: `${TVSHOWS_URL}/batch` });
    expect(batch.request.body).toEqual({
      showIds: [1, 4],
      genres: { add: [], remove: [] },
      tags: { add: ['Kult'], remove: [] },
      mpaa: { clear: false, value: null },
      status: { clear: false, value: null },
    });
    const job = {
      id: 't',
      key: 'tvshows-batch',
      title: '2 Serien bearbeiten',
      status: 'RUNNING',
    } as Job;
    batch.flush(job);
    expect(updated).toEqual([job]);

    completed.next({
      ...job,
      status: 'SUCCEEDED',
      result: { changed: 1, unchanged: 1, failed: 1, failures: ['Serie 4: gesperrt'] },
    });
    await respond(page([alf]));
    expect(snackBarMessages).toContain(
      '2 Serien bearbeiten: 1 geändert, 1 unverändert, 1 Fehler: Serie 4: gesperrt.',
    );
  });

  describe('scraping the selection', () => {
    function report(): HTMLElement | null {
      return element().querySelector<HTMLElement>('.scrape-report');
    }

    it('starts a job for exactly the chosen entries', async () => {
      await respond(page([alf]));
      element().querySelector<HTMLButtonElement>('.choose-all-button')!.click();
      httpTesting.expectOne(`${TVSHOWS_URL}/query/ids`).flush([1, 2]);
      fixture.detectChanges();

      element().querySelector<HTMLButtonElement>('.scrape-chosen-button')!.click();

      const start = httpTesting.expectOne({ method: 'POST', url: `${TVSHOWS_URL}/auto-scrape` });
      expect(start.request.body).toEqual({ showIds: [1, 2] });
      start.flush(scrapeJob);
      expect(updated).toEqual([scrapeJob]);
    });

    it('offers no scraping without a selection', async () => {
      await respond(page([alf]));

      expect(element().querySelector('.scrape-chosen-button')).toBeNull();
    });

    it('shows progress and can cancel', async () => {
      running.set(scrapeJob);
      await respond(page([alf]));

      expect(element().querySelector('.scrape-message')?.textContent).toContain('1 von 2');
      element().querySelector<HTMLButtonElement>('.cancel-scrape-button')!.click();
      expect(updated.map((job) => job.status)).toEqual(['CANCELLED']);
    });

    it('keeps the report open and links what it could not match', async () => {
      await respond(page([alf]));

      completed.next(finishedScrape);
      await respond(page([alf]));
      fixture.detectChanges();

      expect(report()?.textContent).toContain(
        '2 Serien scrapen abgeschlossen: 2 Serien, 1 Episode ergänzt, 1 neu zugeordnet, 1 ohne Treffer, 3 Bilder gespeichert.',
      );
      expect(report()?.querySelector('.unmatched-entries a')?.getAttribute('href')).toBe(
        '/tvshows/2',
      );

      report()!.querySelector<HTMLButtonElement>('.close-report-button')!.click();
      fixture.detectChanges();
      expect(report()).toBeNull();
    });

    it('offers the report of a run that ended while on another page', async () => {
      history.set([finishedScrape]);
      await respond(page([alf]));
      expect(report()).toBeNull();

      element().querySelector<HTMLButtonElement>('.show-scrape-report-button')!.click();
      fixture.detectChanges();

      expect(report()).not.toBeNull();
      expect(element().querySelector('.show-scrape-report-button')).toBeNull();
    });
  });

  it('refreshes the chosen shows in Kodi as a job', async () => {
    await respond(page([alf]));
    element().querySelector<HTMLButtonElement>('.choose-all-button')!.click();
    httpTesting.expectOne(`${TVSHOWS_URL}/query/ids`).flush([1, 4]);
    fixture.detectChanges();

    element().querySelector<HTMLButtonElement>('.refresh-kodi-button')!.click();
    const refresh = httpTesting.expectOne({ method: 'POST', url: '/api/v1/kodi/tvshows/refresh' });
    expect(refresh.request.body).toEqual({ showIds: [1, 4] });
    const job = {
      id: 'k',
      key: 'tvshows-batch',
      title: '2 Serien in Kodi aktualisieren',
      status: 'RUNNING',
    } as Job;
    refresh.flush(job);

    expect(updated).toEqual([job]);
  });

  it('fetches the missing trailers of the chosen shows as a job', async () => {
    await respond(page([alf]));
    element().querySelector<HTMLButtonElement>('.choose-all-button')!.click();
    httpTesting.expectOne(`${TVSHOWS_URL}/query/ids`).flush([1, 4]);
    fixture.detectChanges();

    element().querySelector<HTMLButtonElement>('.fill-trailers-button')!.click();
    const fill = httpTesting.expectOne({ method: 'POST', url: `${TVSHOWS_URL}/trailers/fill` });
    expect(fill.request.body).toEqual({ showIds: [1, 4] });
    const job = {
      id: 't',
      key: 'tvshows-batch',
      title: 'Fehlende Trailer für 2 Serien holen',
      status: 'RUNNING',
    } as Job;
    fill.flush(job);

    expect(updated).toEqual([job]);
  });

  it('refreshes the ratings of the chosen shows as a job', async () => {
    await respond(page([alf]));
    element().querySelector<HTMLButtonElement>('.choose-all-button')!.click();
    httpTesting.expectOne(`${TVSHOWS_URL}/query/ids`).flush([1]);
    fixture.detectChanges();

    element().querySelector<HTMLButtonElement>('.refresh-ratings-button')!.click();
    const fill = httpTesting.expectOne({ method: 'POST', url: `${TVSHOWS_URL}/ratings/refresh` });
    expect(fill.request.body).toEqual({ showIds: [1] });
    const job = {
      id: 'r',
      key: 'tvshows-batch',
      title: 'Bewertungen aktualisieren',
      status: 'RUNNING',
    } as Job;
    fill.flush(job);

    expect(updated).toEqual([job]);
  });

  it('fetches the missing themes of the chosen shows as a job', async () => {
    await respond(page([alf]));
    element().querySelector<HTMLButtonElement>('.choose-all-button')!.click();
    httpTesting.expectOne(`${TVSHOWS_URL}/query/ids`).flush([1]);
    fixture.detectChanges();

    element().querySelector<HTMLButtonElement>('.fill-themes-button')!.click();
    const fill = httpTesting.expectOne({ method: 'POST', url: `${TVSHOWS_URL}/themes/fill` });
    expect(fill.request.body).toEqual({ showIds: [1] });
    const job = {
      id: 't',
      key: 'tvshows-batch',
      title: 'Fehlende Titelmusik',
      status: 'RUNNING',
    } as Job;
    fill.flush(job);

    expect(updated).toEqual([job]);
  });

  it('shows an error when loading fails', async () => {
    fixture.detectChanges();
    httpTesting.match((req) => req.url === FILTERS_URL).forEach((req) => req.flush([]));
    expectShows().flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(element().querySelector('.error')).not.toBeNull();
  });
});
