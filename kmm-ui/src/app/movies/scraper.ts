/** Antwort von GET /api/v1/scrapers/metadata. */
export interface ScraperInfo {
  readonly id: string;
  readonly displayName: string;
  readonly mediaTypes: readonly string[];
  /** Hinweis, was zur Nutzung fehlt, oder {@code null}, wenn der Scraper einsatzbereit ist. */
  readonly missingConfiguration: string | null;
}

/** Suchtreffer eines Scrapers. */
export interface ScrapeCandidate {
  readonly providerId: string;
  readonly title: string;
  readonly originalTitle: string | null;
  readonly year: number | null;
  readonly overview: string | null;
  readonly posterUrl: string | null;
}
