import { ScraperInfo } from '../movies/scraper';

/** Vorgabe für Serien ist TheTVDB, wie in Ember. */
export const PREFERRED_TV_SHOW_SCRAPER = 'tvdb';

/** Der bevorzugte Scraper, sonst der erste eingerichtete, sonst der erste überhaupt. */
export function defaultTvShowScraper(scrapers: readonly ScraperInfo[]): ScraperInfo | undefined {
  const ready = scrapers.filter((scraper) => !scraper.missingConfiguration);
  return (
    ready.find((scraper) => scraper.id === PREFERRED_TV_SHOW_SCRAPER) ??
    ready[0] ??
    scrapers.find((scraper) => scraper.id === PREFERRED_TV_SHOW_SCRAPER) ??
    scrapers[0]
  );
}
