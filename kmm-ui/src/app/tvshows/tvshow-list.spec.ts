import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';

import { FILTERS_URL } from '../filters/saved-filter.service';
import { CONFIRM } from '../shared/confirm';
import { flushWhenRequested } from '../shared/testing';

import { TvShowPage, TvShowSummary } from './tvshow';
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

  beforeEach(() => {
    snackBarMessages = [];
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

  it('lists the shows with year and number of episodes', async () => {
    const request = await respond(
      page([alf, { ...alf, id: 2, title: 'Dark', year: null, episodeCount: 26 }]),
    );

    expect(request.request.params.get('sort')).toBe('title,asc');
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

  it('shows an error when loading fails', async () => {
    fixture.detectChanges();
    httpTesting.match((req) => req.url === FILTERS_URL).forEach((req) => req.flush([]));
    expectShows().flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(element().querySelector('.error')).not.toBeNull();
  });
});
