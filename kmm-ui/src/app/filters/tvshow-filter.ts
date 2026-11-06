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

export type TvShowFilterField =
  | 'TITLE'
  | 'YEAR'
  | 'GENRE'
  | 'TAG'
  | 'STUDIO'
  | 'STATUS'
  | 'MPAA'
  | 'USER_RATING'
  | 'TVDB_ID'
  | 'TMDB_ID'
  | 'IMDB_ID'
  | 'NFO'
  | 'TRAILER'
  | 'SOURCE'
  | 'ADDED_DAYS_AGO'
  | 'EPISODES'
  | 'UNWATCHED_EPISODES'
  | 'EPISODE_RESOLUTION'
  | 'EPISODE_VIDEO_CODEC'
  | 'EPISODE_AUDIO_CODEC'
  | 'EPISODE_AUDIO_LANGUAGE'
  | 'EPISODE_SUBTITLE_LANGUAGE'
  | 'EPISODE_VIDEO_SOURCE'
  | 'EPISODE_STREAM_DETAILS';

export type TvShowFilterCondition = FilterCondition<TvShowFilterField>;

/** Content of POST /api/v1/tvshows/query and definition of a saved TV show filter. */
export type TvShowFilter = ListFilter<TvShowFilterField>;

/** Answer of GET /api/v1/tvshows/filter-values; codecs and languages come from the episodes. */
export interface TvShowFilterValues {
  readonly genres: readonly string[];
  readonly tags: readonly string[];
  readonly studios: readonly string[];
  readonly statuses: readonly string[];
  readonly mpaa: readonly string[];
  readonly videoSources: readonly string[];
  readonly resolutions: readonly number[];
  readonly videoCodecs: readonly string[];
  readonly audioCodecs: readonly string[];
  readonly audioLanguages: readonly string[];
  readonly subtitleLanguages: readonly string[];
}

/**
 * Fields in the order of the selection; the comparisons match TvShowFilterField in the backend. “Episode: …”
 * means at least one episode (“is”) or no episode (“is not”).
 */
export const TVSHOW_FILTER_FIELDS: Readonly<Record<TvShowFilterField, FieldDefinition>> = {
  TITLE: fieldDefinition('fields.title', ['CONTAINS', 'IS', 'IS_NOT'], 'text'),
  YEAR: fieldDefinition('fields.year', NUMBER_OPERATORS, 'number'),
  GENRE: fieldDefinition('fields.genre', LIST_OPERATORS, 'genres'),
  TAG: fieldDefinition('fields.tag', LIST_OPERATORS, 'tags'),
  STUDIO: fieldDefinition('filters.fields.studioNetwork', LIST_OPERATORS, 'studios'),
  STATUS: fieldDefinition('common.status', LIST_OPERATORS, 'statuses'),
  MPAA: fieldDefinition('fields.certification', LIST_OPERATORS, 'mpaa'),
  USER_RATING: fieldDefinition('fields.userRating', NUMBER_OPERATORS, 'number'),
  TVDB_ID: fieldDefinition('fields.tvdbId', PRESENCE_OPERATORS, 'none'),
  TMDB_ID: fieldDefinition('fields.tmdbId', PRESENCE_OPERATORS, 'none'),
  IMDB_ID: fieldDefinition('fields.imdbId', PRESENCE_OPERATORS, 'none'),
  NFO: fieldDefinition('filters.fields.nfo', PRESENCE_OPERATORS, 'none'),
  TRAILER: fieldDefinition('fields.trailer', PRESENCE_OPERATORS, 'none'),
  SOURCE: fieldDefinition('common.source', ['IS', 'IS_NOT'], 'sources'),
  ADDED_DAYS_AGO: fieldDefinition('filters.fields.addedDaysAgo', ['AT_MOST', 'AT_LEAST'], 'number'),
  EPISODES: fieldDefinition('filters.fields.episodes', ['IS', 'AT_LEAST', 'AT_MOST'], 'number'),
  UNWATCHED_EPISODES: fieldDefinition('filters.fields.unwatchedEpisodes', ['IS'], 'boolean'),
  EPISODE_RESOLUTION: fieldDefinition(
    'filters.fields.episodeResolution',
    ['AT_MOST', 'AT_LEAST'],
    'resolutions',
  ),
  EPISODE_VIDEO_CODEC: fieldDefinition(
    'filters.fields.episodeVideoCodec',
    TOKEN_OPERATORS,
    'videoCodecs',
  ),
  EPISODE_AUDIO_CODEC: fieldDefinition(
    'filters.fields.episodeAudioCodec',
    TOKEN_OPERATORS,
    'audioCodecs',
  ),
  EPISODE_AUDIO_LANGUAGE: fieldDefinition(
    'filters.fields.episodeAudioLanguage',
    TOKEN_OPERATORS,
    'audioLanguages',
  ),
  EPISODE_SUBTITLE_LANGUAGE: fieldDefinition(
    'filters.fields.episodeSubtitleLanguage',
    TOKEN_OPERATORS,
    'subtitleLanguages',
  ),
  EPISODE_VIDEO_SOURCE: fieldDefinition(
    'filters.fields.episodeVideoSource',
    TOKEN_OPERATORS,
    'videoSources',
  ),
  EPISODE_STREAM_DETAILS: fieldDefinition(
    'filters.fields.episodeStreamDetails',
    PRESENCE_OPERATORS,
    'none',
  ),
};

export const ALL_TVSHOWS: TvShowFilter = { matchAll: true, conditions: [] };

/** Filters that come with the application; saved ones are added. */
export const PREDEFINED_TVSHOW_FILTERS: readonly PredefinedFilter<TvShowFilterField>[] = [
  predefinedFilter(
    'filters.predefined.withUnwatchedEpisodes',
    only({ field: 'UNWATCHED_EPISODES', operator: 'IS', value: 'true' }),
  ),
  predefinedFilter(
    'filters.predefined.continuing',
    only({ field: 'STATUS', operator: 'IS', value: 'Continuing' }),
  ),
  predefinedFilter(
    'filters.predefined.newLast30Days',
    only({ field: 'ADDED_DAYS_AGO', operator: 'AT_MOST', value: '30' }),
  ),
  predefinedFilter(
    'filters.predefined.episodesBelow720p',
    only({ field: 'EPISODE_RESOLUTION', operator: 'AT_MOST', value: '576' }),
  ),
  predefinedFilter(
    'filters.predefined.withoutTvdbId',
    only({ field: 'TVDB_ID', operator: 'EMPTY', value: null }),
  ),
  predefinedFilter(
    'filters.predefined.episodesWithoutStreamDetails',
    only({ field: 'EPISODE_STREAM_DETAILS', operator: 'EMPTY', value: null }),
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
