import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';

import { provideI18nTesting } from '../i18n/testing';
import { job } from '../jobs/fake-job-events';
import { AutoScrapeResult } from './media-source';
import { autoScrapeSummary, hasScrapeDetails } from './scrape-report';
import { literal } from '../i18n/localized-text';

describe('scrape report', () => {
  const empty: AutoScrapeResult = {
    movies: 1,
    scraped: 0,
    unmatched: 0,
    imagesSaved: 0,
    unmatchedEntries: [],
    failures: [],
    warnings: [],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideI18nTesting()] });
    TestBed.inject(TranslocoService);
  });

  it('uses the singular for a single entry', () => {
    expect(
      autoScrapeSummary(
        job({ title: literal('Scrapen „Heat“'), status: 'SUCCEEDED', result: empty }),
      ),
    ).toBe(
      'Scrapen „Heat“ abgeschlossen: 1 Film, 0 neu zugeordnet, 0 ohne Treffer, 0 Bilder gespeichert.',
    );
    expect(
      autoScrapeSummary(
        job({
          title: literal('Scrapen „Lost“'),
          status: 'SUCCEEDED',
          result: { ...empty, movies: undefined, shows: 1, episodesUpdated: 1 },
        }),
      ),
    ).toContain('1 Serie, 1 Episode ergänzt,');
  });

  it('summarizes in the active UI language with plurals', () => {
    TestBed.inject(TranslocoService).setActiveLang('en');

    expect(
      autoScrapeSummary(
        job({
          title: literal('Scrape'),
          status: 'SUCCEEDED',
          result: { ...empty, imagesSaved: 1, themesSaved: 2, failures: [literal('x')] },
        }),
      ),
    ).toBe(
      'Scrape finished: 1 movie, 0 newly matched, 0 without a match, 1 artwork saved, ' +
        '2 theme music files saved, 1 error.',
    );
    expect(autoScrapeSummary(job({ title: literal('X'), status: 'CANCELLED' }))).toBe(
      'X cancelled.',
    );
  });

  it('reports cancelled and failed runs', () => {
    expect(autoScrapeSummary(job({ title: literal('X'), status: 'CANCELLED' }))).toBe(
      'X abgebrochen.',
    );
    expect(
      autoScrapeSummary(job({ title: literal('X'), status: 'FAILED', message: literal('kaputt') })),
    ).toBe('X fehlgeschlagen: kaputt');
  });

  it('has details when entries beyond the listed ones stayed unmatched', () => {
    expect(hasScrapeDetails(empty)).toBe(false);
    expect(hasScrapeDetails({ ...empty, unmatched: 1 })).toBe(true);
    expect(hasScrapeDetails({ ...empty, warnings: [literal('x')] })).toBe(true);
    expect(hasScrapeDetails({ ...empty, failures: [literal('x')] })).toBe(true);
  });
});
