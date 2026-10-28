/** Einstellungen, die durch die Server-Konfiguration festgelegt sein können. */
export type SettingsField = 'METADATA_LANGUAGE' | 'ARTWORK_LANGUAGE' | 'PROXY';

export interface ProxyView {
  readonly host: string;
  readonly port: number;
  readonly username: string | null;
  readonly passwordSet: boolean;
}

export type EpisodeOrdering = 'AIRED' | 'DVD' | 'ABSOLUTE';

/** Einstellungen für Serien, getrennt von denen für Filme. */
export interface TvSettings {
  readonly metadataLanguage: string;
  readonly artworkLanguage: string;
  readonly episodeOrdering: EpisodeOrdering;
}

/** Einstellungen für ffprobe, das die technischen Angaben ausliest. */
export interface MediaInfoSettings {
  readonly ffprobePath: string;
  /** Nach dem automatischen Scrapen auslesen, wie Embers „MetaDataScan“. */
  readonly probeOnScrape: boolean;
  /** Neue Dateien ohne Angaben in der NFO nach dem Scan auslesen. */
  readonly probeNewFiles: boolean;
}

/** Muster und Auslöser für das Umbenennen, z. B. `{title}[ ({year})]`. */
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

export type ApiKeyName =
  'TMDB_API_KEY' | 'TVDB_API_KEY' | 'TVDB_PIN' | 'FANARTTV_API_KEY' | 'FANARTTV_CLIENT_KEY';

/** Zustand eines API-Keys; der Key selbst wird nie ausgeliefert. */
export interface ApiKeyView {
  readonly key: ApiKeyName;
  readonly pluginId: string;
  readonly label: string;
  readonly set: boolean;
  readonly hint: string | null;
}

/** Antwort von GET /api/v1/settings. */
export interface SettingsView {
  readonly metadataLanguage: string;
  readonly artworkLanguage: string;
  readonly videoExtensions: readonly string[];
  readonly excludedPaths: readonly string[];
  readonly proxy: ProxyView | null;
  readonly tv: TvSettings;
  readonly mediaInfo: MediaInfoSettings;
  readonly rename: RenameSettings;
  readonly overridden: readonly SettingsField[];
}

/** Proxy in einer Änderung; ein Passwort von `null` lässt das gespeicherte unverändert. */
export interface ProxySettings {
  readonly host: string;
  readonly port: number;
  readonly username: string | null;
  readonly password: string | null;
}

/** Inhalt von PUT /api/v1/settings. */
export interface KmmSettings {
  readonly metadataLanguage: string;
  readonly artworkLanguage: string;
  readonly videoExtensions: readonly string[];
  readonly excludedPaths: readonly string[];
  readonly proxy: ProxySettings | null;
  readonly tv: TvSettings;
  readonly mediaInfo: MediaInfoSettings;
  readonly rename: RenameSettings;
}
