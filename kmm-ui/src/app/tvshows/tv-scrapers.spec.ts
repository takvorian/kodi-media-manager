import { ScraperInfo } from '../movies/scraper';
import { defaultTvShowScraper } from './tv-scrapers';

describe('defaultTvShowScraper', () => {
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
  };
  const unconfiguredTvdb: ScraperInfo = { ...tvdb, missingConfiguration: 'Key fehlt' };

  it('prefers a ready TVDB scraper', () => {
    expect(defaultTvShowScraper([tmdb, tvdb])).toBe(tvdb);
  });

  it('falls back to the first ready scraper', () => {
    expect(defaultTvShowScraper([unconfiguredTvdb, tmdb])).toBe(tmdb);
  });

  it('keeps TVDB when no scraper is ready', () => {
    const unconfiguredTmdb = { ...tmdb, missingConfiguration: 'Key fehlt' };
    expect(defaultTvShowScraper([unconfiguredTmdb, unconfiguredTvdb])).toBe(unconfiguredTvdb);
    expect(defaultTvShowScraper([unconfiguredTmdb])).toBe(unconfiguredTmdb);
    expect(defaultTvShowScraper([])).toBeUndefined();
  });
});
