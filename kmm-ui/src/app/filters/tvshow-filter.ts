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

/** Inhalt von POST /api/v1/tvshows/query und Definition eines gespeicherten Serienfilters. */
export type TvShowFilter = ListFilter<TvShowFilterField>;

/** Antwort von GET /api/v1/tvshows/filter-values; Codecs und Sprachen stammen aus den Episoden. */
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
 * Felder in der Reihenfolge der Auswahl; die Vergleiche entsprechen TvShowFilterField im Backend. „Episode …“
 * heißt: mindestens eine Episode („ist“) bzw. keine Episode („ist nicht“).
 */
export const TVSHOW_FILTER_FIELDS: Readonly<Record<TvShowFilterField, FieldDefinition>> = {
  TITLE: { label: 'Titel', operators: ['CONTAINS', 'IS', 'IS_NOT'], values: 'text' },
  YEAR: { label: 'Jahr', operators: NUMBER_OPERATORS, values: 'number' },
  GENRE: { label: 'Genre', operators: LIST_OPERATORS, values: 'genres' },
  TAG: { label: 'Tag', operators: LIST_OPERATORS, values: 'tags' },
  STUDIO: { label: 'Studio/Sender', operators: LIST_OPERATORS, values: 'studios' },
  STATUS: { label: 'Status', operators: LIST_OPERATORS, values: 'statuses' },
  MPAA: { label: 'Altersfreigabe', operators: LIST_OPERATORS, values: 'mpaa' },
  USER_RATING: { label: 'Eigene Bewertung', operators: NUMBER_OPERATORS, values: 'number' },
  TVDB_ID: { label: 'TVDB-ID', operators: PRESENCE_OPERATORS, values: 'none' },
  TMDB_ID: { label: 'TMDB-ID', operators: PRESENCE_OPERATORS, values: 'none' },
  IMDB_ID: { label: 'IMDb-ID', operators: PRESENCE_OPERATORS, values: 'none' },
  NFO: { label: 'NFO', operators: PRESENCE_OPERATORS, values: 'none' },
  SOURCE: { label: 'Quelle', operators: ['IS', 'IS_NOT'], values: 'sources' },
  ADDED_DAYS_AGO: {
    label: 'Hinzugefügt vor Tagen',
    operators: ['AT_MOST', 'AT_LEAST'],
    values: 'number',
  },
  EPISODES: {
    label: 'Anzahl Episoden',
    operators: ['IS', 'AT_LEAST', 'AT_MOST'],
    values: 'number',
  },
  UNWATCHED_EPISODES: { label: 'Ungesehene Episoden', operators: ['IS'], values: 'boolean' },
  EPISODE_RESOLUTION: {
    label: 'Episode: Auflösung',
    operators: ['AT_MOST', 'AT_LEAST'],
    values: 'resolutions',
  },
  EPISODE_VIDEO_CODEC: {
    label: 'Episode: Video-Codec',
    operators: TOKEN_OPERATORS,
    values: 'videoCodecs',
  },
  EPISODE_AUDIO_CODEC: {
    label: 'Episode: Audio-Codec',
    operators: TOKEN_OPERATORS,
    values: 'audioCodecs',
  },
  EPISODE_AUDIO_LANGUAGE: {
    label: 'Episode: Audiosprache',
    operators: TOKEN_OPERATORS,
    values: 'audioLanguages',
  },
  EPISODE_SUBTITLE_LANGUAGE: {
    label: 'Episode: Untertitelsprache',
    operators: TOKEN_OPERATORS,
    values: 'subtitleLanguages',
  },
  EPISODE_VIDEO_SOURCE: {
    label: 'Episode: Videoquelle',
    operators: TOKEN_OPERATORS,
    values: 'videoSources',
  },
  EPISODE_STREAM_DETAILS: {
    label: 'Episode: technische Angaben',
    operators: PRESENCE_OPERATORS,
    values: 'none',
  },
};

export const ALL_TVSHOWS: TvShowFilter = { matchAll: true, conditions: [] };

/** Mitgelieferte Filter; gespeicherte kommen dazu. */
export const PREDEFINED_TVSHOW_FILTERS: readonly PredefinedFilter<TvShowFilterField>[] = [
  {
    name: 'Mit ungesehenen Episoden',
    filter: only({ field: 'UNWATCHED_EPISODES', operator: 'IS', value: 'true' }),
  },
  {
    name: 'Laufende Serien',
    filter: only({ field: 'STATUS', operator: 'IS', value: 'Continuing' }),
  },
  {
    name: 'Neu (letzte 30 Tage)',
    filter: only({ field: 'ADDED_DAYS_AGO', operator: 'AT_MOST', value: '30' }),
  },
  {
    name: 'Episoden unter 720p',
    filter: only({ field: 'EPISODE_RESOLUTION', operator: 'AT_MOST', value: '576' }),
  },
  { name: 'Ohne TVDB-ID', filter: only({ field: 'TVDB_ID', operator: 'EMPTY', value: null }) },
  {
    name: 'Episoden ohne technische Angaben',
    filter: only({ field: 'EPISODE_STREAM_DETAILS', operator: 'EMPTY', value: null }),
  },
  { name: 'Ohne NFO', filter: only({ field: 'NFO', operator: 'EMPTY', value: null }) },
];
