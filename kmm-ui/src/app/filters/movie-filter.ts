/** Vergleiche einer Filterbedingung; muss zu FilterOperator im Backend passen. */
export type FilterOperator =
  'IS' | 'IS_NOT' | 'CONTAINS' | 'AT_LEAST' | 'AT_MOST' | 'EMPTY' | 'NOT_EMPTY';

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

export interface FilterCondition {
  readonly field: MovieFilterField;
  readonly operator: FilterOperator;
  readonly value: string | null;
}

/** Inhalt von POST /api/v1/movies/query und Definition eines gespeicherten Filmfilters. */
export interface MovieFilter {
  readonly search?: string | null;
  readonly matchAll: boolean;
  readonly conditions: readonly FilterCondition[];
}

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

/** Eine Auswahlmöglichkeit für den Vergleichswert. */
export interface FilterOption {
  readonly value: string;
  readonly label: string;
}

/**
 * Woher die Auswahl für den Wert kommt: vorhandene Werte, ja/nein, Quellen oder freie Eingabe (Text bzw. Zahl).
 */
export type ValueSource =
  keyof MovieFilterValues | 'boolean' | 'sources' | 'text' | 'number' | 'none';

export interface FieldDefinition {
  readonly label: string;
  readonly operators: readonly FilterOperator[];
  readonly values: ValueSource;
}

const LIST: readonly FilterOperator[] = ['IS', 'IS_NOT', 'EMPTY', 'NOT_EMPTY'];
const NUMBER: readonly FilterOperator[] = ['IS', 'AT_LEAST', 'AT_MOST', 'EMPTY', 'NOT_EMPTY'];
const PRESENCE: readonly FilterOperator[] = ['EMPTY', 'NOT_EMPTY'];
const TOKEN: readonly FilterOperator[] = ['IS', 'IS_NOT'];

/** Felder in der Reihenfolge der Auswahl; die Vergleiche entsprechen MovieFilterField im Backend. */
export const MOVIE_FILTER_FIELDS: Readonly<Record<MovieFilterField, FieldDefinition>> = {
  TITLE: { label: 'Titel', operators: ['CONTAINS', 'IS', 'IS_NOT'], values: 'text' },
  YEAR: { label: 'Jahr', operators: NUMBER, values: 'number' },
  GENRE: { label: 'Genre', operators: LIST, values: 'genres' },
  TAG: { label: 'Tag', operators: LIST, values: 'tags' },
  COUNTRY: { label: 'Land', operators: LIST, values: 'countries' },
  STUDIO: { label: 'Studio', operators: LIST, values: 'studios' },
  SET: { label: 'Filmset', operators: LIST, values: 'sets' },
  WATCHED: { label: 'Gesehen', operators: ['IS'], values: 'boolean' },
  USER_RATING: { label: 'Eigene Bewertung', operators: NUMBER, values: 'number' },
  MPAA: { label: 'Altersfreigabe', operators: LIST, values: 'mpaa' },
  EDITION: { label: 'Fassung', operators: LIST, values: 'editions' },
  VIDEO_SOURCE: { label: 'Videoquelle', operators: LIST, values: 'videoSources' },
  RESOLUTION: { label: 'Auflösung', operators: NUMBER, values: 'resolutions' },
  VIDEO_CODEC: { label: 'Video-Codec', operators: LIST, values: 'videoCodecs' },
  HDR: { label: 'HDR', operators: LIST, values: 'hdrTypes' },
  AUDIO_CODEC: { label: 'Audio-Codec', operators: TOKEN, values: 'audioCodecs' },
  AUDIO_LANGUAGE: { label: 'Audiosprache', operators: TOKEN, values: 'audioLanguages' },
  SUBTITLE_LANGUAGE: { label: 'Untertitelsprache', operators: TOKEN, values: 'subtitleLanguages' },
  STREAM_DETAILS: { label: 'Technische Angaben', operators: PRESENCE, values: 'none' },
  TMDB_ID: { label: 'TMDB-ID', operators: PRESENCE, values: 'none' },
  IMDB_ID: { label: 'IMDb-ID', operators: PRESENCE, values: 'none' },
  NFO: { label: 'NFO', operators: PRESENCE, values: 'none' },
  SOURCE: { label: 'Quelle', operators: ['IS', 'IS_NOT'], values: 'sources' },
  ADDED_DAYS_AGO: {
    label: 'Hinzugefügt vor Tagen',
    operators: ['AT_MOST', 'AT_LEAST'],
    values: 'number',
  },
};

export const OPERATOR_LABELS: Readonly<Record<FilterOperator, string>> = {
  IS: 'ist',
  IS_NOT: 'ist nicht',
  CONTAINS: 'enthält',
  AT_LEAST: 'mindestens',
  AT_MOST: 'höchstens',
  EMPTY: 'fehlt',
  NOT_EMPTY: 'vorhanden',
};

export function needsValue(operator: FilterOperator): boolean {
  return operator !== 'EMPTY' && operator !== 'NOT_EMPTY';
}

/** Auflösungsstufe wie Kodi, z. B. 2160 → „4K“. */
export function resolutionLabel(lines: number): string {
  return lines === 2160 ? '4K' : lines === 4320 ? '8K' : `${lines}p`;
}

export const ALL_MOVIES: MovieFilter = { matchAll: true, conditions: [] };

/** Mitgelieferte Filter; gespeicherte kommen dazu. */
export const PREDEFINED_MOVIE_FILTERS: readonly {
  readonly name: string;
  readonly filter: MovieFilter;
}[] = [
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

function only(condition: FilterCondition): MovieFilter {
  return { matchAll: true, conditions: [condition] };
}
