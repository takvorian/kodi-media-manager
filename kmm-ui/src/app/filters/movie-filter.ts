import {
  FieldDefinition,
  FilterCondition,
  LIST_OPERATORS,
  ListFilter,
  NUMBER_OPERATORS,
  PRESENCE_OPERATORS,
  PredefinedFilter,
  TOKEN_OPERATORS,
  fieldDefinition,
  only,
  predefinedFilter,
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
  | 'TRAILER'
  | 'SOURCE'
  | 'ADDED_DAYS_AGO';

export type MovieFilterCondition = FilterCondition<MovieFilterField>;

/** Content of POST /api/v1/movies/query and definition of a saved movie filter. */
export type MovieFilter = ListFilter<MovieFilterField>;

/** Answer of GET /api/v1/movies/filter-values. */
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

/** Fields in the order of the selection; the comparisons match MovieFilterField in the backend. */
export const MOVIE_FILTER_FIELDS: Readonly<Record<MovieFilterField, FieldDefinition>> = {
  TITLE: fieldDefinition('fields.title', ['CONTAINS', 'IS', 'IS_NOT'], 'text'),
  YEAR: fieldDefinition('fields.year', NUMBER_OPERATORS, 'number'),
  GENRE: fieldDefinition('fields.genre', LIST_OPERATORS, 'genres'),
  TAG: fieldDefinition('fields.tag', LIST_OPERATORS, 'tags'),
  COUNTRY: fieldDefinition('fields.country', LIST_OPERATORS, 'countries'),
  STUDIO: fieldDefinition('fields.studio', LIST_OPERATORS, 'studios'),
  SET: fieldDefinition('fields.movieSet', LIST_OPERATORS, 'sets'),
  WATCHED: fieldDefinition('fields.watched', ['IS'], 'boolean'),
  USER_RATING: fieldDefinition('fields.userRating', NUMBER_OPERATORS, 'number'),
  MPAA: fieldDefinition('fields.certification', LIST_OPERATORS, 'mpaa'),
  EDITION: fieldDefinition('fields.edition', LIST_OPERATORS, 'editions'),
  VIDEO_SOURCE: fieldDefinition('fields.videoSource', LIST_OPERATORS, 'videoSources'),
  RESOLUTION: fieldDefinition('fields.resolution', NUMBER_OPERATORS, 'resolutions'),
  VIDEO_CODEC: fieldDefinition('fields.videoCodec', LIST_OPERATORS, 'videoCodecs'),
  HDR: fieldDefinition('filters.fields.hdr', LIST_OPERATORS, 'hdrTypes'),
  AUDIO_CODEC: fieldDefinition('fields.audioCodec', TOKEN_OPERATORS, 'audioCodecs'),
  AUDIO_LANGUAGE: fieldDefinition(
    'filters.fields.audioLanguage',
    TOKEN_OPERATORS,
    'audioLanguages',
  ),
  SUBTITLE_LANGUAGE: fieldDefinition(
    'filters.fields.subtitleLanguage',
    TOKEN_OPERATORS,
    'subtitleLanguages',
  ),
  STREAM_DETAILS: fieldDefinition('filters.fields.streamDetails', PRESENCE_OPERATORS, 'none'),
  TMDB_ID: fieldDefinition('fields.tmdbId', PRESENCE_OPERATORS, 'none'),
  IMDB_ID: fieldDefinition('fields.imdbId', PRESENCE_OPERATORS, 'none'),
  NFO: fieldDefinition('filters.fields.nfo', PRESENCE_OPERATORS, 'none'),
  TRAILER: fieldDefinition('fields.trailer', PRESENCE_OPERATORS, 'none'),
  SOURCE: fieldDefinition('common.source', ['IS', 'IS_NOT'], 'sources'),
  ADDED_DAYS_AGO: fieldDefinition('filters.fields.addedDaysAgo', ['AT_MOST', 'AT_LEAST'], 'number'),
};

export const ALL_MOVIES: MovieFilter = { matchAll: true, conditions: [] };

/** Filters that come with the application; saved ones are added. */
export const PREDEFINED_MOVIE_FILTERS: readonly PredefinedFilter<MovieFilterField>[] = [
  predefinedFilter(
    'filters.predefined.unwatched',
    only({ field: 'WATCHED', operator: 'IS', value: 'false' }),
  ),
  predefinedFilter(
    'filters.predefined.newLast30Days',
    only({ field: 'ADDED_DAYS_AGO', operator: 'AT_MOST', value: '30' }),
  ),
  predefinedFilter(
    'filters.predefined.below720p',
    only({ field: 'RESOLUTION', operator: 'AT_MOST', value: '576' }),
  ),
  predefinedFilter(
    'filters.predefined.uhd4k',
    only({ field: 'RESOLUTION', operator: 'AT_LEAST', value: '2160' }),
  ),
  predefinedFilter(
    'filters.predefined.withoutTmdbId',
    only({ field: 'TMDB_ID', operator: 'EMPTY', value: null }),
  ),
  predefinedFilter(
    'filters.predefined.withoutStreamDetails',
    only({ field: 'STREAM_DETAILS', operator: 'EMPTY', value: null }),
  ),
  predefinedFilter(
    'filters.predefined.withoutNfo',
    only({ field: 'NFO', operator: 'EMPTY', value: null }),
  ),
  predefinedFilter(
    'filters.predefined.withoutTrailer',
    only({ field: 'TRAILER', operator: 'EMPTY', value: null }),
  ),
];
