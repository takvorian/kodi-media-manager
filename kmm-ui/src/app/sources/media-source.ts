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

/** Antwort von POST /api/v1/sources/{id}/scan. */
export interface ScanResult {
  readonly sourceId: number;
  readonly added: number;
  readonly removed: number;
  readonly unchanged: number;
}
