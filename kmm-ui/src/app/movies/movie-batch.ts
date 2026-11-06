import { translate } from '@jsverse/transloco';

import { BatchField, BatchFieldKey, FieldChange, ListChange } from '../filters/batch';

/** Job key of the batch edit; must match MovieBatchService.JOB_KEY. */
export const MOVIE_BATCH_JOB_KEY = 'movies-batch';

/** Job key for scraping a selection; must match AutoScrapeService.MOVIES_JOB_KEY. */
export const MOVIES_SCRAPE_JOB_KEY = 'movies-scrape';

/** Job key for scraping one movie; must match AutoScrapeService.MOVIE_JOB_KEY_PREFIX. */
export function movieScrapeJobKey(movieId: number): string {
  return `movie-scrape:${movieId}`;
}

/** A batch field whose label is translated each time it is read, so it follows a language switch. */
function translatedField(key: BatchFieldKey, labelKey: string): BatchField {
  return {
    key,
    get label() {
      return translate(labelKey);
    },
  };
}

/** Single fields the batch edit of movies can set or clear. */
export const MOVIE_BATCH_FIELDS: readonly BatchField[] = [
  translatedField('mpaa', 'fields.certification'),
  translatedField('edition', 'fields.edition'),
  translatedField('videoSource', 'fields.videoSource'),
];

/** Body of POST /api/v1/movies/batch. */
export interface MovieBatchEdit {
  readonly movieIds: readonly number[];
  readonly genres: ListChange;
  readonly tags: ListChange;
  readonly mpaa: FieldChange;
  readonly edition: FieldChange;
  readonly videoSource: FieldChange;
}
