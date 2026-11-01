import { BatchField, FieldChange, ListChange } from '../filters/batch';

/** Job-Schlüssel der Stapel-Bearbeitung; muss zu TvShowBatchService.JOB_KEY passen. */
export const TVSHOW_BATCH_JOB_KEY = 'tvshows-batch';

/** Job-Schlüssel für das Scrapen einer Auswahl; muss zu AutoScrapeService.TV_SHOWS_JOB_KEY passen. */
export const TVSHOWS_SCRAPE_JOB_KEY = 'tvshows-scrape';

/** Job-Schlüssel für das Scrapen einer Serie; muss zu AutoScrapeService.TV_SHOW_JOB_KEY_PREFIX passen. */
export function tvShowScrapeJobKey(showId: number): string {
  return `tvshow-scrape:${showId}`;
}

/** Einzelne Felder, die die Stapel-Bearbeitung von Serien setzen oder leeren kann. */
export const TVSHOW_BATCH_FIELDS: readonly BatchField[] = [
  { key: 'mpaa', label: 'Altersfreigabe' },
  { key: 'status', label: 'Status' },
];

/** Inhalt von POST /api/v1/tvshows/batch. */
export interface TvShowBatchEdit {
  readonly showIds: readonly number[];
  readonly genres: ListChange;
  readonly tags: ListChange;
  readonly mpaa: FieldChange;
  readonly status: FieldChange;
}
