import { Observable } from 'rxjs';

export type ArtworkType =
  | 'POSTER'
  | 'FANART'
  | 'CLEARLOGO'
  | 'CLEARART'
  | 'DISCART'
  | 'BANNER'
  | 'LANDSCAPE'
  | 'CHARACTERART'
  | 'THUMB';

export const ARTWORK_LABELS: Record<ArtworkType, string> = {
  POSTER: 'Poster',
  FANART: 'Fanart',
  CLEARLOGO: 'Clearlogo',
  CLEARART: 'Clearart',
  DISCART: 'Disc',
  BANNER: 'Banner',
  LANDSCAPE: 'Landscape',
  CHARACTERART: 'Characterart',
  THUMB: 'Standbild',
};

/** Antwort von GET /api/v1/movies/{id}/artwork. */
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
  /** Staffel des Bildes oder `null` für das ganze Medium. */
  readonly season?: number | null;
}

export interface ArtworkCandidates {
  readonly candidates: readonly ArtworkCandidate[];
  readonly warnings: readonly string[];
}

/** Woher eine Bildergalerie ihre Bilder bezieht: ein Film, eine Serie, eine Staffel oder eine Episode. */
export interface ArtworkSource {
  findAll(): Observable<ArtworkInfo[]>;
  /** URL des Bildes; die Änderungszeit sorgt dafür, dass der Browser ein ersetztes Bild neu lädt. */
  imageUrl(artwork: ArtworkInfo): string;
  candidates(type: ArtworkType): Observable<ArtworkCandidates>;
  /** Lädt das Bild und speichert es nach Kodi-Konvention. */
  apply(type: ArtworkType, url: string): Observable<ArtworkInfo>;
  remove(type: ArtworkType): Observable<void>;
}

/** Hängt die Änderungszeit als Versionsparameter an die Bild-URL. */
export function versioned(url: string, artwork: ArtworkInfo): string {
  if (!artwork.modifiedAt) {
    return url;
  }
  return `${url}${url.includes('?') ? '&' : '?'}v=${encodeURIComponent(artwork.modifiedAt)}`;
}
