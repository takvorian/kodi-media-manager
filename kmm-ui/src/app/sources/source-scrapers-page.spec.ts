import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';

import { provideI18nTesting } from '../i18n/testing';
import { sourcePrioritiesUrl } from '../settings/scrapers/scraper-admin.service';
import { ScraperPriority } from '../settings/scrapers/scraper-plugin';
import { SOURCES_URL } from './media-source.service';
import { SourceScrapersPage } from './source-scrapers-page';

const PRIORITIES: ScraperPriority[] = [
  {
    kind: 'METADATA',
    mediaType: 'TV_SHOW',
    inherited: true,
    entries: [
      { scraperId: 'tvdb', displayName: 'TheTVDB', enabled: true, missingConfiguration: null },
      {
        scraperId: 'tmdb',
        displayName: 'TMDB',
        enabled: true,
        missingConfiguration: { key: 'scraper.apiKeyMissing', args: { provider: 'TMDB' } },
      },
    ],
  },
  {
    kind: 'ARTWORK',
    mediaType: 'TV_SHOW',
    inherited: false,
    entries: [
      {
        scraperId: 'fanarttv',
        displayName: 'fanart.tv',
        enabled: false,
        missingConfiguration: null,
      },
    ],
  },
];

describe('SourceScrapersPage', () => {
  let httpTesting: HttpTestingController;
  let snackBarMessages: string[];

  beforeEach(() => {
    snackBarMessages = [];
    TestBed.configureTestingModule({
      providers: [
        provideI18nTesting(),
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: MatSnackBar,
          useValue: { open: (message: string) => snackBarMessages.push(message) },
        },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('lets a kind follow the settings or get its own order', async () => {
    const fixture = TestBed.createComponent(SourceScrapersPage);
    fixture.componentRef.setInput('id', '4');
    fixture.detectChanges();
    httpTesting
      .expectOne(SOURCES_URL)
      .flush([{ id: 4, name: 'Serien', mediaType: 'TV_SHOW', path: '/serien' }]);
    httpTesting.expectOne(sourcePrioritiesUrl(4)).flush(PRIORITIES);
    await fixture.whenStable();
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('.page-title')?.textContent).toContain('„Serien“');
    expect(element.textContent).toContain('Für TMDB fehlt ein API-Key');
    const metadata = element.querySelector('[data-kind="METADATA"]')!;
    expect(metadata.querySelector<HTMLButtonElement>('.down')!.disabled).toBe(true);

    metadata.querySelector<HTMLInputElement>('.inherit input')!.click();
    fixture.detectChanges();
    metadata.querySelector<HTMLButtonElement>('.down')!.click();
    fixture.detectChanges();
    element.querySelector<HTMLButtonElement>('.save-priorities-button')!.click();

    const request = httpTesting.expectOne({ method: 'PUT', url: sourcePrioritiesUrl(4) });
    expect(request.request.body).toEqual([
      {
        kind: 'METADATA',
        mediaType: 'TV_SHOW',
        inherit: false,
        entries: [
          { scraperId: 'tmdb', enabled: true },
          { scraperId: 'tvdb', enabled: true },
        ],
      },
      {
        kind: 'ARTWORK',
        mediaType: 'TV_SHOW',
        inherit: false,
        entries: [{ scraperId: 'fanarttv', enabled: false }],
      },
    ]);
    request.flush(PRIORITIES);
    await fixture.whenStable();
    expect(snackBarMessages).toEqual(['Reihenfolge gespeichert.']);
  });
});
