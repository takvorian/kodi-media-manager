export type DiscFormat = 'FILE' | 'DVD' | 'BLURAY';

export interface MovieSummary {
  readonly id: number;
  readonly sourceId: number;
  readonly title: string;
  readonly year: number | null;
  readonly filePath: string;
  readonly discFormat: DiscFormat;
  readonly partCount: number;
  readonly addedAt: string;
}

/** Antwort von GET /api/v1/movies. */
export interface MoviePage {
  readonly content: readonly MovieSummary[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}

export interface MovieQuery {
  readonly search: string;
  readonly page: number;
  readonly size: number;
}

/** Bearbeitbare Metadaten, entspricht dem Inhalt der NFO. */
export interface MovieMetadata {
  readonly title: string;
  readonly originalTitle: string | null;
  readonly sortTitle: string | null;
  readonly year: number | null;
  readonly premiered: string | null;
  readonly plot: string | null;
  readonly outline: string | null;
  readonly tagline: string | null;
  readonly runtime: number | null;
  readonly mpaa: string | null;
  readonly genres: readonly string[];
  readonly imdbId: string | null;
  readonly tmdbId: string | null;
}

export interface NfoInfo {
  readonly path: string;
  readonly exists: boolean;
  readonly modifiedAt: string | null;
}

/** Antwort von GET /api/v1/movies/{id}. */
export interface MovieDetails {
  readonly id: number;
  readonly sourceId: number;
  readonly filePath: string;
  readonly discFormat: DiscFormat;
  readonly partCount: number;
  readonly addedAt: string;
  readonly metadata: MovieMetadata;
  readonly nfo: NfoInfo;
}
