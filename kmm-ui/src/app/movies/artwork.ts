export type ArtworkType = 'POSTER' | 'FANART' | 'CLEARLOGO' | 'CLEARART' | 'DISCART' | 'BANNER' | 'LANDSCAPE';

export const ARTWORK_LABELS: Record<ArtworkType, string> = {
  POSTER: 'Poster',
  FANART: 'Fanart',
  CLEARLOGO: 'Clearlogo',
  CLEARART: 'Clearart',
  DISCART: 'Disc',
  BANNER: 'Banner',
  LANDSCAPE: 'Landscape',
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
}

export interface ArtworkCandidates {
  readonly candidates: readonly ArtworkCandidate[];
  readonly warnings: readonly string[];
}
