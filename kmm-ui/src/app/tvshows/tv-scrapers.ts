import { ScraperInfo } from '../movies/scraper';

/** TheTVDB is the default for TV shows, as in Ember. */
export const PREFERRED_TV_SHOW_SCRAPER = 'tvdb';

/** The preferred scraper, otherwise the first configured one, otherwise the very first. */
export function defaultTvShowScraper(scrapers: readonly ScraperInfo[]): ScraperInfo | undefined {
  const ready = scrapers.filter((scraper) => !scraper.missingConfiguration);
  return (
    ready.find((scraper) => scraper.id === PREFERRED_TV_SHOW_SCRAPER) ??
    ready[0] ??
    scrapers.find((scraper) => scraper.id === PREFERRED_TV_SHOW_SCRAPER) ??
    scrapers[0]
  );
}
