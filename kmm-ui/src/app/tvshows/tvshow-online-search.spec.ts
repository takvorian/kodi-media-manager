import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';

import { literal } from '../i18n/localized-text';
import { provideI18nTesting } from '../i18n/testing';
import { ScrapeCandidate, ScraperInfo } from '../movies/scraper';
import { TvShowProposal } from './tvshow';
import { TvShowOnlineSearch, TvShowScrapedProposal } from './tvshow-online-search';
import { TVSHOWS_URL, TV_SHOW_SCRAPERS_URL } from './tvshow.service';

describe('TvShowOnlineSearch', () => {
  const tmdb: ScraperInfo = {
    id: 'tmdb',
    displayName: 'TMDB',
    mediaTypes: ['TV_SHOW'],
    missingConfiguration: null,
  };
  const tvdb: ScraperInfo = {
    id: 'tvdb',
    displayName: 'TheTVDB',
    mediaTypes: ['TV_SHOW'],
    missingConfiguration: null,
    attribution: {
      text: literal('Metadata provided by TheTVDB.'),
      link: 'https://www.thetvdb.com',
    },
  };
  const lost: ScrapeCandidate = {
    providerId: '73739',
    title: 'Lost',
    originalTitle: 'Lost',
    year: 2004,
    overview: 'Überlebende eines Flugzeugabsturzes.',
    posterUrl: null,
  };
  const proposal = {
    metadata: { title: 'Lost' },
    matchedEpisodes: 3,
    providerEpisodes: 120,
  } as TvShowProposal;

  let fixture: ComponentFixture<TvShowOnlineSearch>;
  let httpTesting: HttpTestingController;
  let proposals: TvShowScrapedProposal[];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TvShowOnlineSearch],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideI18nTesting()],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TvShowOnlineSearch);
    fixture.componentRef.setInput('showId', 7);
    fixture.componentRef.setInput('title', 'Lost');
    fixture.componentRef.setInput('year', 2004);
    proposals = [];
    fixture.componentInstance.proposed.subscribe((proposed) => proposals.push(proposed));
  });

  afterEach(() => httpTesting.verify());

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  async function loadScrapers(scrapers: ScraperInfo[]): Promise<void> {
    fixture.detectChanges();
    httpTesting.expectOne(TV_SHOW_SCRAPERS_URL).flush(scrapers);
    await fixture.whenStable();
  }

  function searchButton(): HTMLButtonElement {
    return element().querySelector<HTMLButtonElement>('.search-button')!;
  }

  function expectSearch(): TestRequest {
    return httpTesting.expectOne((request) => request.url === `${TVSHOWS_URL}/7/scraper-search`);
  }

  async function type(name: string, value: string): Promise<void> {
    const input = element().querySelector<HTMLInputElement>(`[name="${name}"]`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  it('searches at TheTVDB by default and shows its attribution', async () => {
    await loadScrapers([tmdb, tvdb]);

    expect(searchButton().textContent).toContain('Bei TheTVDB suchen');
    expect(element().querySelector('.attribution a')?.getAttribute('href')).toBe(
      'https://www.thetvdb.com',
    );
    searchButton().click();
    const request = expectSearch();
    request.flush([lost]);
    await fixture.whenStable();

    expect(request.request.params.get('scraper')).toBe('tvdb');
    expect(request.request.params.get('title')).toBe('Lost');
    expect(request.request.params.get('year')).toBe('2004');
    expect(
      element().querySelector('.candidate-title')?.textContent?.replace(/\s+/g, ' ').trim(),
    ).toBe('Lost (2004)');
  });

  it('switches the texts to English at runtime', async () => {
    await loadScrapers([tmdb]);
    searchButton().click();
    expectSearch().flush([]);
    await fixture.whenStable();

    TestBed.inject(TranslocoService).setActiveLang('en');
    await fixture.whenStable();

    expect(searchButton().textContent?.trim()).toBe('Search TMDB');
    expect(element().querySelector('.no-results')?.textContent).toContain('No results.');
    expect(element().querySelector('.query-field mat-label')?.textContent).toBe('Search term');
  });

  it('searches with a custom term and year', async () => {
    await loadScrapers([tmdb]);
    await type('query', 'Verschollen');
    await type('searchYear', '2005');

    searchButton().click();
    const request = expectSearch();
    request.flush([]);
    await fixture.whenStable();

    expect(request.request.params.get('title')).toBe('Verschollen');
    expect(request.request.params.get('year')).toBe('2005');
    expect(element().querySelector('.no-results')).not.toBeNull();
    expect(element().querySelector('.attribution')).toBeNull();
  });

  it('emits the proposal of the chosen hit with its origin', async () => {
    await loadScrapers([tvdb]);
    searchButton().click();
    expectSearch().flush([lost]);
    await fixture.whenStable();

    element().querySelector<HTMLButtonElement>('.adopt-button')!.click();
    httpTesting.expectOne((req) => req.url === `${TVSHOWS_URL}/7/scraper-proposal`).flush(proposal);

    expect(proposals).toEqual([
      { proposal, scraperId: 'tvdb', scraperName: 'TheTVDB', providerId: '73739' },
    ]);
  });

  it('shows provider errors and missing configuration', async () => {
    await loadScrapers([{ ...tvdb, missingConfiguration: literal('Für TVDB fehlt ein API-Key.') }]);

    expect(element().querySelector('.missing-configuration')?.textContent).toContain('API-Key');
    expect(searchButton().disabled).toBe(true);
  });

  it('reports failed searches', async () => {
    await loadScrapers([tvdb]);

    searchButton().click();
    expectSearch().flush(
      { detail: 'TVDB nicht erreichbar' },
      { status: 502, statusText: 'Bad Gateway' },
    );
    await fixture.whenStable();

    expect(element().querySelector('.error')?.textContent).toContain('TVDB nicht erreichbar');
  });

  it('explains when no scraper is available', async () => {
    await loadScrapers([]);

    expect(element().querySelector('.no-scraper')?.textContent).toContain('Serien-Scraper');
  });
});
