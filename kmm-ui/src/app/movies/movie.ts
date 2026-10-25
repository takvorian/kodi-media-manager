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
