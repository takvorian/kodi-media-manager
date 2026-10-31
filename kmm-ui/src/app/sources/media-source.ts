export type MediaType = 'MOVIE' | 'MOVIE_SET' | 'TV_SHOW' | 'TV_SEASON' | 'TV_EPISODE';

/** Medienarten, für die eine Quelle angelegt werden kann. */
export const SOURCE_MEDIA_TYPES: readonly { readonly value: MediaType; readonly label: string }[] =
  [
    { value: 'MOVIE', label: 'Filme' },
    { value: 'TV_SHOW', label: 'Serien' },
  ];

/** Antwort von GET /api/v1/sources. */
export interface MediaSource {
  readonly id: number;
  readonly name: string;
  readonly path: string;
  readonly mediaType: MediaType;
  readonly createdAt: string;
  readonly lastScannedAt: string | null;
}

export interface CreateMediaSource {
  readonly name: string;
  readonly path: string;
  readonly mediaType: MediaType;
}

/** Präfix des Job-Schlüssels eines Scans; muss zu MediaSourceService.SCAN_JOB_KEY_PREFIX passen. */
export const SCAN_JOB_KEY_PREFIX = 'scan:';

export function scanJobKey(sourceId: number): string {
  return `${SCAN_JOB_KEY_PREFIX}${sourceId}`;
}

/** Ein Eintrag im Bericht eines Scans; ohne ID, wenn er nicht mehr in der Bibliothek steht. */
export interface ScanEntry {
  readonly type: MediaType;
  readonly id: number | null;
  readonly label: string;
}

/** Was ein Scan geändert hat; die Listen sind serverseitig gekürzt. */
export interface ScanDetails {
  readonly added: readonly ScanEntry[];
  readonly updated: readonly ScanEntry[];
  readonly removed: readonly ScanEntry[];
  readonly addedShows: readonly ScanEntry[];
  readonly removedShows: readonly ScanEntry[];
  readonly unreadableNfos: readonly string[];
}

/** Ergebnis eines erfolgreichen Scan-Jobs. */
/** Ergebnis eines Scans; bei Serienquellen zählen die Felder Episoden. */
export interface ScanResult {
  readonly sourceId: number;
  readonly added: number;
  readonly updated: number;
  readonly removed: number;
  readonly unchanged: number;
  readonly nfoErrors: number;
  readonly details?: ScanDetails;
  /** Nur bei Serien: neu gefundene Serien. */
  readonly showsAdded?: number;
  /** Nur bei Serien: verschwundene Serien. */
  readonly showsRemoved?: number;
  /** Nur bei Serien: Videodateien ohne erkennbare Staffel und Episode. */
  readonly unrecognizedFiles?: readonly string[];
}

/** Präfix des Job-Schlüssels beim automatischen Scrapen; muss zu AutoScrapeService.JOB_KEY_PREFIX passen. */
export const AUTO_SCRAPE_JOB_KEY_PREFIX = 'auto-scrape:';

export function autoScrapeJobKey(sourceId: number): string {
  return `${AUTO_SCRAPE_JOB_KEY_PREFIX}${sourceId}`;
}

/** Ergebnis eines erfolgreichen Scrape-Jobs; die Listen sind serverseitig gekürzt. */
/** Ergebnis des automatischen Scrapens einer Film- oder Serienquelle. */
export interface AutoScrapeResult {
  /** Bearbeitete Filme (nur bei Filmquellen). */
  readonly movies?: number;
  /** Bearbeitete Serien (nur bei Serienquellen). */
  readonly shows?: number;
  /** Episoden mit ergänzten Angaben (nur bei Serienquellen). */
  readonly episodesUpdated?: number;
  readonly scraped: number;
  readonly unmatched: number;
  readonly imagesSaved: number;
  /** Neu eingetragene Trailer-Links. */
  readonly trailersSaved?: number;
  /** Neu gespeicherte Titelmusik (theme.mp3). */
  readonly themesSaved?: number;
  /** Einträge mit ergänzten Bewertungen oder Feldern (etwa Trakt, OMDb). */
  readonly enriched?: number;
  readonly unmatchedTitles: readonly string[];
  readonly failures: readonly string[];
  readonly warnings: readonly string[];
}
