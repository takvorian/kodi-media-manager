import { translate } from '@jsverse/transloco';

import { BatchField, BatchFieldKey, FieldChange, ListChange } from '../filters/batch';

/** Job key of the batch edit; must match TvShowBatchService.JOB_KEY. */
export const TVSHOW_BATCH_JOB_KEY = 'tvshows-batch';

/** Job key for scraping a selection; must match AutoScrapeService.TV_SHOWS_JOB_KEY. */
export const TVSHOWS_SCRAPE_JOB_KEY = 'tvshows-scrape';

/** Job key for scraping one TV show; must match AutoScrapeService.TV_SHOW_JOB_KEY_PREFIX. */
export function tvShowScrapeJobKey(showId: number): string {
  return `tvshow-scrape:${showId}`;
}

/** A batch field whose label is translated whenever it is read, so it follows the active language. */
function translatedField(key: BatchFieldKey, labelKey: string): BatchField {
  return {
    key,
    get label() {
      return translate(labelKey);
    },
  };
}

/** Single fields the batch edit of TV shows can set or clear. */
export const TVSHOW_BATCH_FIELDS: readonly BatchField[] = [
  translatedField('mpaa', 'fields.certification'),
  translatedField('status', 'common.status'),
];

/** Body of POST /api/v1/tvshows/batch. */
export interface TvShowBatchEdit {
  readonly showIds: readonly number[];
  readonly genres: ListChange;
  readonly tags: ListChange;
  readonly mpaa: FieldChange;
  readonly status: FieldChange;
}
