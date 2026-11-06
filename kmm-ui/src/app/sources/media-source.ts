import { LocalizedText } from '../i18n/localized-text';

export type MediaType = 'MOVIE' | 'MOVIE_SET' | 'TV_SHOW' | 'TV_SEASON' | 'TV_EPISODE';

/** Media types a source can be created for; the label is a translation key. */
export const SOURCE_MEDIA_TYPES: readonly {
  readonly value: MediaType;
  readonly labelKey: string;
}[] = [
  { value: 'MOVIE', labelKey: 'sources.mediaType.MOVIE' },
  { value: 'TV_SHOW', labelKey: 'sources.mediaType.TV_SHOW' },
];

/** Response of GET /api/v1/sources. */
export interface MediaSource {
  readonly id: number;
  readonly name: string;
  readonly path: string;
  readonly mediaType: MediaType;
  readonly createdAt: string;
  readonly lastScannedAt: string | null;
  /** Language of the entries when scraping; without one the settings apply. */
  readonly metadataLanguage: string | null;
}

export interface CreateMediaSource {
  readonly name: string;
  readonly path: string;
  readonly mediaType: MediaType;
  readonly metadataLanguage: string | null;
}

/** Prefix of the job key of a scan; must match MediaSourceService.SCAN_JOB_KEY_PREFIX. */
export const SCAN_JOB_KEY_PREFIX = 'scan:';

export function scanJobKey(sourceId: number): string {
  return `${SCAN_JOB_KEY_PREFIX}${sourceId}`;
}

/** An entry in the report of a scan; without ID if it is no longer in the library. */
export interface ScanEntry {
  readonly type: MediaType;
  readonly id: number | null;
  readonly label: string;
}

/** What a scan changed; the lists are truncated by the server. */
export interface ScanDetails {
  readonly added: readonly ScanEntry[];
  readonly updated: readonly ScanEntry[];
  readonly removed: readonly ScanEntry[];
  readonly addedShows: readonly ScanEntry[];
  readonly removedShows: readonly ScanEntry[];
  readonly unreadableNfos: readonly string[];
}

/** Result of a successful scan job; for TV show sources the fields count episodes. */
export interface ScanResult {
  readonly sourceId: number;
  readonly added: number;
  readonly updated: number;
  readonly removed: number;
  readonly unchanged: number;
  readonly nfoErrors: number;
  readonly details?: ScanDetails;
  /** TV shows only: newly found shows. */
  readonly showsAdded?: number;
  /** TV shows only: shows that disappeared. */
  readonly showsRemoved?: number;
  /** TV shows only: video files without a recognizable season and episode. */
  readonly unrecognizedFiles?: readonly string[];
}

/** Prefix of the job key of auto-scraping; must match AutoScrapeService.JOB_KEY_PREFIX. */
export const AUTO_SCRAPE_JOB_KEY_PREFIX = 'auto-scrape:';

export function autoScrapeJobKey(sourceId: number): string {
  return `${AUTO_SCRAPE_JOB_KEY_PREFIX}${sourceId}`;
}

/** Result of auto-scraping movies or TV shows; the lists are truncated by the server. */
export interface AutoScrapeResult {
  /** Processed movies (movies only). */
  readonly movies?: number;
  /** Processed TV shows (TV shows only). */
  readonly shows?: number;
  /** Episodes with completed details (TV shows only). */
  readonly episodesUpdated?: number;
  readonly scraped: number;
  readonly unmatched: number;
  readonly imagesSaved: number;
  /** Newly added trailer links. */
  readonly trailersSaved?: number;
  /** Newly saved theme music (theme.mp3). */
  readonly themesSaved?: number;
  /** Entries with completed ratings or fields (e.g. Trakt, OMDb). */
  readonly enriched?: number;
  /** Entries without a unique match; may be shorter than {@link unmatched}. */
  readonly unmatchedEntries: readonly ScanEntry[];
  /** Failures per entry, e.g. "Heat: TMDB cannot be reached". */
  readonly failures: readonly LocalizedText[];
  readonly warnings: readonly LocalizedText[];
}
