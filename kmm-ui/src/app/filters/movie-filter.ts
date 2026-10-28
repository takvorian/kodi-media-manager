import {
  FieldDefinition,
  FilterCondition,
  LIST_OPERATORS,
  ListFilter,
  NUMBER_OPERATORS,
  PRESENCE_OPERATORS,
  PredefinedFilter,
  TOKEN_OPERATORS,
  only,
} from './filter';

export type { FieldDefinition, FilterOperator, FilterOption, ListFilter } from './filter';
export { OPERATOR_LABELS, needsValue, resolutionLabel } from './filter';

export type MovieFilterField =
  | 'TITLE'
  | 'YEAR'
  | 'GENRE'
  | 'TAG'
  | 'COUNTRY'
  | 'STUDIO'
  | 'SET'
  | 'WATCHED'
  | 'USER_RATING'
  | 'MPAA'
  | 'EDITION'
  | 'VIDEO_SOURCE'
  | 'RESOLUTION'
  | 'VIDEO_CODEC'
  | 'HDR'
  | 'AUDIO_CODEC'
  | 'AUDIO_LANGUAGE'
  | 'SUBTITLE_LANGUAGE'
  | 'STREAM_DETAILS'
  | 'TMDB_ID'
  | 'IMDB_ID'
  | 'NFO'
  | 'SOURCE'
  | 'ADDED_DAYS_AGO';

export type MovieFilterCondition = FilterCondition<MovieFilterField>;

/** Inhalt von POST /api/v1/movies/query und Definition eines gespeicherten Filmfilters. */
export type MovieFilter = ListFilter<MovieFilterField>;

/** Antwort von GET /api/v1/movies/filter-values. */
export interface MovieFilterValues {
  readonly genres: readonly string[];
  readonly tags: readonly string[];
  readonly countries: readonly string[];
  readonly studios: readonly string[];
  readonly sets: readonly string[];
  readonly mpaa: readonly string[];
  readonly editions: readonly string[];
  readonly videoSources: readonly string[];
  readonly resolutions: readonly number[];
  readonly videoCodecs: readonly string[];
  readonly hdrTypes: readonly string[];
  readonly audioCodecs: readonly string[];
  readonly audioLanguages: readonly string[];
  readonly subtitleLanguages: readonly string[];
}

/** Felder in der Reihenfolge der Auswahl; die Vergleiche entsprechen MovieFilterField im Backend. */
export const MOVIE_FILTER_FIELDS: Readonly<Record<MovieFilterField, FieldDefinition>> = {
  TITLE: { label: 'Titel', operators: ['CONTAINS', 'IS', 'IS_NOT'], values: 'text' },
  YEAR: { label: 'Jahr', operators: NUMBER_OPERATORS, values: 'number' },
  GENRE: { label: 'Genre', operators: LIST_OPERATORS, values: 'genres' },
  TAG: { label: 'Tag', operators: LIST_OPERATORS, values: 'tags' },
  COUNTRY: { label: 'Land', operators: LIST_OPERATORS, values: 'countries' },
  STUDIO: { label: 'Studio', operators: LIST_OPERATORS, values: 'studios' },
  SET: { label: 'Filmset', operators: LIST_OPERATORS, values: 'sets' },
  WATCHED: { label: 'Gesehen', operators: ['IS'], values: 'boolean' },
  USER_RATING: { label: 'Eigene Bewertung', operators: NUMBER_OPERATORS, values: 'number' },
  MPAA: { label: 'Altersfreigabe', operators: LIST_OPERATORS, values: 'mpaa' },
  EDITION: { label: 'Fassung', operators: LIST_OPERATORS, values: 'editions' },
  VIDEO_SOURCE: { label: 'Videoquelle', operators: LIST_OPERATORS, values: 'videoSources' },
  RESOLUTION: { label: 'Auflösung', operators: NUMBER_OPERATORS, values: 'resolutions' },
  VIDEO_CODEC: { label: 'Video-Codec', operators: LIST_OPERATORS, values: 'videoCodecs' },
  HDR: { label: 'HDR', operators: LIST_OPERATORS, values: 'hdrTypes' },
  AUDIO_CODEC: { label: 'Audio-Codec', operators: TOKEN_OPERATORS, values: 'audioCodecs' },
  AUDIO_LANGUAGE: { label: 'Audiosprache', operators: TOKEN_OPERATORS, values: 'audioLanguages' },
  SUBTITLE_LANGUAGE: {
    label: 'Untertitelsprache',
    operators: TOKEN_OPERATORS,
    values: 'subtitleLanguages',
  },
  STREAM_DETAILS: { label: 'Technische Angaben', operators: PRESENCE_OPERATORS, values: 'none' },
  TMDB_ID: { label: 'TMDB-ID', operators: PRESENCE_OPERATORS, values: 'none' },
  IMDB_ID: { label: 'IMDb-ID', operators: PRESENCE_OPERATORS, values: 'none' },
  NFO: { label: 'NFO', operators: PRESENCE_OPERATORS, values: 'none' },
  SOURCE: { label: 'Quelle', operators: ['IS', 'IS_NOT'], values: 'sources' },
  ADDED_DAYS_AGO: {
    label: 'Hinzugefügt vor Tagen',
    operators: ['AT_MOST', 'AT_LEAST'],
    values: 'number',
  },
};

export const ALL_MOVIES: MovieFilter = { matchAll: true, conditions: [] };

/** Mitgelieferte Filter; gespeicherte kommen dazu. */
export const PREDEFINED_MOVIE_FILTERS: readonly PredefinedFilter<MovieFilterField>[] = [
  { name: 'Ungesehen', filter: only({ field: 'WATCHED', operator: 'IS', value: 'false' }) },
  {
    name: 'Neu (letzte 30 Tage)',
    filter: only({ field: 'ADDED_DAYS_AGO', operator: 'AT_MOST', value: '30' }),
  },
  { name: 'Unter 720p', filter: only({ field: 'RESOLUTION', operator: 'AT_MOST', value: '576' }) },
  { name: '4K', filter: only({ field: 'RESOLUTION', operator: 'AT_LEAST', value: '2160' }) },
  { name: 'Ohne TMDB-ID', filter: only({ field: 'TMDB_ID', operator: 'EMPTY', value: null }) },
  {
    name: 'Ohne technische Angaben',
    filter: only({ field: 'STREAM_DETAILS', operator: 'EMPTY', value: null }),
  },
  { name: 'Ohne NFO', filter: only({ field: 'NFO', operator: 'EMPTY', value: null }) },
];
