import { StreamDetails } from '../shared/stream-details';

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
  /** Fassung, z. B. „Director's Cut“. */
  readonly edition: string | null;
  /** Videoquelle, z. B. bluray. */
  readonly videoSource: string | null;
}

export interface MovieActor {
  readonly name: string;
  readonly role: string | null;
  readonly thumb: string | null;
}

export interface MovieRating {
  readonly source: string;
  readonly value: number;
  readonly max: number | null;
  readonly votes: number | null;
  readonly defaultRating: boolean;
}

export interface MovieSetReference {
  readonly name: string;
  readonly overview: string | null;
}

/** Weitere Angaben neben den bearbeitbaren Metadaten (derzeit nur lesend). */
export interface MovieSupplement {
  readonly actors: readonly MovieActor[];
  readonly directors: readonly string[];
  readonly writers: readonly string[];
  readonly studios: readonly string[];
  readonly countries: readonly string[];
  readonly tags: readonly string[];
  readonly ratings: readonly MovieRating[];
  readonly set: MovieSetReference | null;
  readonly userRating: number | null;
  readonly playCount: number | null;
  readonly lastPlayed: string | null;
}

export const EMPTY_SUPPLEMENT: MovieSupplement = {
  actors: [],
  directors: [],
  writers: [],
  studios: [],
  countries: [],
  tags: [],
  ratings: [],
  set: null,
  userRating: null,
  playCount: null,
  lastPlayed: null,
};

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
  readonly supplement: MovieSupplement;
  readonly streams: StreamDetails;
  readonly nfo: NfoInfo;
}
