import { autoScrapeSummary, hasScrapeDetails } from './scrape-report';
import { AutoScrapeResult } from './media-source';
import { job } from '../jobs/fake-job-events';

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

  it('uses the singular for a single entry', () => {
    expect(
      autoScrapeSummary(job({ title: 'Scrapen „Heat“', status: 'SUCCEEDED', result: empty })),
    ).toBe(
      'Scrapen „Heat“ abgeschlossen: 1 Film, 0 neu zugeordnet, 0 ohne Treffer, 0 Bilder gespeichert.',
    );
    expect(
      autoScrapeSummary(
        job({
          title: 'Scrapen „Lost“',
          status: 'SUCCEEDED',
          result: { ...empty, movies: undefined, shows: 1, episodesUpdated: 1 },
        }),
      ),
    ).toContain('1 Serie, 1 Episode ergänzt,');
  });

  it('reports cancelled and failed runs', () => {
    expect(autoScrapeSummary(job({ title: 'X', status: 'CANCELLED' }))).toBe('X abgebrochen.');
    expect(autoScrapeSummary(job({ title: 'X', status: 'FAILED', message: 'kaputt' }))).toBe(
      'X fehlgeschlagen: kaputt',
    );
  });

  it('has details when entries beyond the listed ones stayed unmatched', () => {
    expect(hasScrapeDetails(empty)).toBe(false);
    expect(hasScrapeDetails({ ...empty, unmatched: 1 })).toBe(true);
    expect(hasScrapeDetails({ ...empty, warnings: ['x'] })).toBe(true);
    expect(hasScrapeDetails({ ...empty, failures: ['x'] })).toBe(true);
  });
});
