export type MediaType = 'MOVIE' | 'MOVIE_SET' | 'TV_SHOW' | 'TV_SEASON' | 'TV_EPISODE';

/** Medienarten, für die eine Quelle angelegt werden kann. */
export const SOURCE_MEDIA_TYPES: readonly { readonly value: MediaType; readonly label: string }[] = [
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

/** Präfix des Job-Schlüssels eines Scans; muss zu MovieScanService.JOB_KEY_PREFIX passen. */
export const SCAN_JOB_KEY_PREFIX = 'movie-scan:';

export function scanJobKey(sourceId: number): string {
  return `${SCAN_JOB_KEY_PREFIX}${sourceId}`;
}

/** Ergebnis eines erfolgreichen Scan-Jobs. */
export interface ScanResult {
  readonly sourceId: number;
  readonly added: number;
  readonly updated: number;
  readonly removed: number;
  readonly unchanged: number;
  readonly nfoErrors: number;
}
