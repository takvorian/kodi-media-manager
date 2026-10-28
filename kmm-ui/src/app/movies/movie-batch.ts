import { BatchField, FieldChange, ListChange } from '../filters/batch';

/** Job-Schlüssel der Stapel-Bearbeitung; muss zu MovieBatchService.JOB_KEY passen. */
export const MOVIE_BATCH_JOB_KEY = 'movies-batch';

/** Einzelne Felder, die die Stapel-Bearbeitung von Filmen setzen oder leeren kann. */
export const MOVIE_BATCH_FIELDS: readonly BatchField[] = [
  { key: 'mpaa', label: 'Altersfreigabe' },
  { key: 'edition', label: 'Fassung' },
  { key: 'videoSource', label: 'Videoquelle' },
];

/** Inhalt von POST /api/v1/movies/batch. */
export interface MovieBatchEdit {
  readonly movieIds: readonly number[];
  readonly genres: ListChange;
  readonly tags: ListChange;
  readonly mpaa: FieldChange;
  readonly edition: FieldChange;
  readonly videoSource: FieldChange;
}
