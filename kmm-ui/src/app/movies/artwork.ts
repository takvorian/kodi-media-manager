import { Observable } from 'rxjs';

import { LocalizedText } from '../i18n/localized-text';

export type ArtworkType =
  | 'POSTER'
  | 'FANART'
  | 'CLEARLOGO'
  | 'CLEARART'
  | 'DISCART'
  | 'BANNER'
  | 'LANDSCAPE'
  | 'CHARACTERART'
  | 'KEYART'
  | 'THUMB';

/** Translation key of the label of each artwork type. */
export const ARTWORK_LABEL_KEYS: Record<ArtworkType, string> = {
  POSTER: 'movies.artwork.type.POSTER',
  FANART: 'movies.artwork.type.FANART',
  CLEARLOGO: 'movies.artwork.type.CLEARLOGO',
  CLEARART: 'movies.artwork.type.CLEARART',
  DISCART: 'movies.artwork.type.DISCART',
  BANNER: 'movies.artwork.type.BANNER',
  LANDSCAPE: 'movies.artwork.type.LANDSCAPE',
  CHARACTERART: 'movies.artwork.type.CHARACTERART',
  KEYART: 'movies.artwork.type.KEYART',
  THUMB: 'movies.artwork.type.THUMB',
};

/** Response of GET /api/v1/movies/{id}/artwork. */
export interface ArtworkInfo {
  readonly type: ArtworkType;
  readonly exists: boolean;
  readonly path: string | null;
  readonly modifiedAt: string | null;
}

export interface ArtworkCandidate {
  readonly scraperId: string;
  readonly scraperName: string;
  readonly type: ArtworkType;
  readonly url: string;
  readonly previewUrl: string;
  readonly width: number | null;
  readonly height: number | null;
  readonly language: string | null;
  readonly rating: number | null;
  /** Season of the image, or `null` for the whole medium. */
  readonly season?: number | null;
}

export interface ArtworkCandidates {
  readonly candidates: readonly ArtworkCandidate[];
  readonly warnings: readonly LocalizedText[];
}

/** Where an artwork gallery gets its images from: a movie, a movie set, a TV show, a season or an episode. */
export interface ArtworkSource {
  findAll(): Observable<ArtworkInfo[]>;
  /** URL of the image; the modification time makes the browser reload a replaced image. */
  imageUrl(artwork: ArtworkInfo): string;
  candidates(type: ArtworkType): Observable<ArtworkCandidates>;
  /** Downloads the image and saves it following the Kodi convention. */
  apply(type: ArtworkType, url: string): Observable<ArtworkInfo>;
  remove(type: ArtworkType): Observable<void>;
}

/** Appends the modification time to the image URL as a version parameter. */
export function versioned(url: string, artwork: ArtworkInfo): string {
  if (!artwork.modifiedAt) {
    return url;
  }
  return `${url}${url.includes('?') ? '&' : '?'}v=${encodeURIComponent(artwork.modifiedAt)}`;
}
