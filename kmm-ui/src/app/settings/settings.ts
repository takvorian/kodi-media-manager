/** Settings that may be fixed by the server configuration. */
export type SettingsField = 'METADATA_LANGUAGE' | 'ARTWORK_LANGUAGE' | 'PROXY';

export interface ProxyView {
  readonly host: string;
  readonly port: number;
  readonly username: string | null;
  readonly passwordSet: boolean;
}

export type EpisodeOrdering = 'AIRED' | 'DVD' | 'ABSOLUTE';

/** Settings for TV shows, separate from those for movies. */
export interface TvSettings {
  readonly metadataLanguage: string;
  readonly artworkLanguage: string;
  readonly episodeOrdering: EpisodeOrdering;
}

/** Settings for ffprobe, which reads the technical details. */
export interface MediaInfoSettings {
  readonly ffprobePath: string;
  /** Read after auto-scraping, like Ember's "MetaDataScan". */
  readonly probeOnScrape: boolean;
  /** Read new files without details in the NFO after the scan. */
  readonly probeNewFiles: boolean;
}

/** Patterns and triggers for renaming, e.g. `{title}[ ({year})]`. */
export interface RenameSettings {
  readonly movieFolder: string;
  readonly movieFile: string;
  readonly tvShowFolder: string;
  readonly seasonFolder: string;
  readonly episodeFile: string;
  readonly movieOnEdit: boolean;
  readonly movieOnScrape: boolean;
  readonly tvOnEdit: boolean;
  readonly tvOnScrape: boolean;
  readonly episodesOnScan: boolean;
}

/** Storage of the movie set artwork (flat scheme and Kodi's set folders) and assignment when scraping. */
export interface MovieSetSettings {
  readonly flatImages: boolean;
  readonly flatImagesFolder: string | null;
  readonly kodiImages: boolean;
  readonly kodiImagesFolder: string | null;
  /** Assign movies to the set of their TMDB collection when scraping. */
  readonly assignOnScrape: boolean;
}

export type ApiKeyName =
  | 'TMDB_API_KEY'
  | 'TVDB_API_KEY'
  | 'TVDB_PIN'
  | 'FANARTTV_API_KEY'
  | 'FANARTTV_CLIENT_KEY'
  | 'TRAKT_CLIENT_ID'
  | 'OMDB_API_KEY';

/** State of an API key; the key itself is never delivered. */
export interface ApiKeyView {
  readonly key: ApiKeyName;
  readonly pluginId: string;
  readonly label: string;
  readonly set: boolean;
  readonly hint: string | null;
}

/** Response of GET /api/v1/settings. */
export interface SettingsView {
  readonly metadataLanguage: string;
  readonly artworkLanguage: string;
  readonly videoExtensions: readonly string[];
  readonly excludedPaths: readonly string[];
  readonly proxy: ProxyView | null;
  readonly tv: TvSettings;
  readonly mediaInfo: MediaInfoSettings;
  readonly rename: RenameSettings;
  readonly movieSets: MovieSetSettings;
  readonly overridden: readonly SettingsField[];
}

/** Proxy in a change; a password of `null` leaves the stored one unchanged. */
export interface ProxySettings {
  readonly host: string;
  readonly port: number;
  readonly username: string | null;
  readonly password: string | null;
}

/** Body of PUT /api/v1/settings. */
export interface KmmSettings {
  readonly metadataLanguage: string;
  readonly artworkLanguage: string;
  readonly videoExtensions: readonly string[];
  readonly excludedPaths: readonly string[];
  readonly proxy: ProxySettings | null;
  readonly tv: TvSettings;
  readonly mediaInfo: MediaInfoSettings;
  readonly rename: RenameSettings;
  readonly movieSets: MovieSetSettings;
}
