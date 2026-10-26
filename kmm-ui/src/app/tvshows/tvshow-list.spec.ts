import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

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

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TvShowList],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
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

  async function respond(showPage: TvShowPage): Promise<TestRequest> {
    fixture.detectChanges();
    const request = httpTesting.expectOne((req) => req.url === TVSHOWS_URL);
    request.flush(showPage);
    await fixture.whenStable();
    return request;
  }

  it('lists the shows with year and number of episodes', async () => {
    const request = await respond(page([alf, { ...alf, id: 2, title: 'Dark', year: null, episodeCount: 26 }]));

    expect(request.request.params.get('sort')).toBe('title,asc');
    expect(Array.from(element().querySelectorAll('.tvshow-title a')).map((link) => link.getAttribute('href'))).toEqual([
      '/tvshows/1',
      '/tvshows/2',
    ]);
    expect(Array.from(element().querySelectorAll('.episode-count')).map((cell) => cell.textContent)).toEqual([
      '99',
      '26',
    ]);
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
    await new Promise((resolve) => setTimeout(resolve, SEARCH_DEBOUNCE_MS + DEBOUNCE_SAFETY_MARGIN_MS));
    const searched = await respond(page([]));
    expect(searched.request.params.get('search')).toBe('alf');
    expect(element().querySelector('.empty')?.textContent).toContain('Keine Serien gefunden');
  });

  it('shows an error when loading fails', async () => {
    fixture.detectChanges();
    httpTesting
      .expectOne((req) => req.url === TVSHOWS_URL)
      .flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(element().querySelector('.error')).not.toBeNull();
  });
});
