import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MovieMetadata } from './movie';
import { MovieOnlineSearch, ScrapedProposal } from './movie-online-search';
import { METADATA_SCRAPERS_URL, MOVIES_URL } from './movie.service';
import { ScrapeCandidate, ScraperInfo } from './scraper';

describe('MovieOnlineSearch', () => {
  const tmdb: ScraperInfo = { id: 'tmdb', displayName: 'TMDB', mediaTypes: ['MOVIE'], missingConfiguration: null };
  const other: ScraperInfo = { id: 'other', displayName: 'Andere', mediaTypes: ['MOVIE'], missingConfiguration: null };
  const heat: ScrapeCandidate = {
    providerId: '949',
    title: 'Stirb langsam',
    originalTitle: 'Die Hard',
    year: 1988,
    overview: 'John McClane gegen Hans Gruber.',
    posterUrl: 'https://image.tmdb.org/t/p/w185/diehard.jpg',
  };
  const proposal = { title: 'Stirb langsam', genres: ['Action'] } as unknown as MovieMetadata;

  let fixture: ComponentFixture<MovieOnlineSearch>;
  let httpTesting: HttpTestingController;
  let proposals: ScrapedProposal[];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MovieOnlineSearch],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MovieOnlineSearch);
    fixture.componentRef.setInput('movieId', 5);
    fixture.componentRef.setInput('title', 'Die Hard');
    fixture.componentRef.setInput('year', 1988);
    proposals = [];
    fixture.componentInstance.proposed.subscribe((proposed) => proposals.push(proposed));
  });

  afterEach(() => httpTesting.verify());

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  async function loadScrapers(scrapers: ScraperInfo[]): Promise<void> {
    fixture.detectChanges();
    httpTesting.expectOne(METADATA_SCRAPERS_URL).flush(scrapers);
    await fixture.whenStable();
  }

  function searchButton(): HTMLButtonElement {
    return element().querySelector<HTMLButtonElement>('.search-button')!;
  }

  function expectSearch(): TestRequest {
    return httpTesting.expectOne((request) => request.url === `${MOVIES_URL}/5/scraper-search`);
  }

  async function type(name: string, value: string): Promise<void> {
    const input = element().querySelector<HTMLInputElement>(`[name="${name}"]`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  it('searches with title and year of the movie by default and lists the hits', async () => {
    await loadScrapers([tmdb]);

    searchButton().click();
    const request = expectSearch();
    request.flush([heat]);
    await fixture.whenStable();

    expect(request.request.params.get('scraper')).toBe('tmdb');
    expect(request.request.params.get('title')).toBe('Die Hard');
    expect(request.request.params.get('year')).toBe('1988');
    expect(element().querySelector('.candidate-title')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      'Stirb langsam (1988)',
    );
    expect(element().querySelector('.candidate')?.textContent).toContain('Die Hard');
    expect(element().querySelector<HTMLImageElement>('.poster')?.src).toBe(heat.posterUrl);
    expect(searchButton().textContent).toContain('Bei TMDB suchen');
  });

  it('searches with a custom term and only the year entered for it', async () => {
    await loadScrapers([tmdb]);
    await type('query', 'Stirb langsam');

    searchButton().click();
    const request = expectSearch();
    request.flush([]);
    await fixture.whenStable();

    expect(request.request.params.get('title')).toBe('Stirb langsam');
    expect(request.request.params.has('year')).toBe(false);
    expect(element().querySelector('.no-results')).not.toBeNull();
  });

  it('emits the proposal of the chosen hit', async () => {
    await loadScrapers([tmdb]);
    searchButton().click();
    expectSearch().flush([heat]);
    await fixture.whenStable();

    element().querySelector<HTMLButtonElement>('.adopt-button')!.click();
    const request = httpTesting.expectOne((req) => req.url === `${MOVIES_URL}/5/scraper-proposal`);
    request.flush(proposal);

    expect(request.request.params.get('providerId')).toBe('949');
    expect(proposals).toEqual([{ metadata: proposal, scraperName: 'TMDB' }]);
  });

  it('shows provider errors', async () => {
    await loadScrapers([tmdb]);

    searchButton().click();
    expectSearch().flush({ detail: 'TMDB lehnt den API-Key ab.' }, { status: 502, statusText: 'Bad Gateway' });
    await fixture.whenStable();

    expect(element().querySelector('.error')?.textContent).toContain('API-Key');
    expect(searchButton().disabled).toBe(false);
  });

  it('explains a missing configuration and blocks searching', async () => {
    await loadScrapers([{ ...tmdb, missingConfiguration: 'Für TMDB fehlt ein API-Key.' }]);

    expect(element().querySelector('.missing-configuration')?.textContent).toContain('API-Key');
    expect(searchButton().disabled).toBe(true);
  });

  it('offers a choice between several scrapers', async () => {
    await loadScrapers([tmdb, other]);

    expect(element().querySelector('mat-select')).not.toBeNull();
  });

  it('explains when no scraper is available', async () => {
    await loadScrapers([]);

    expect(element().querySelector('.no-scraper')).not.toBeNull();
  });

  it('shows an error when the scrapers cannot be loaded', async () => {
    fixture.detectChanges();
    httpTesting.expectOne(METADATA_SCRAPERS_URL).flush('boom', { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(element().querySelector('.error')).not.toBeNull();
  });
});
