/** Antwort von GET /api/v1/scrapers/metadata. */
export interface ScraperInfo {
  readonly id: string;
  readonly displayName: string;
  readonly mediaTypes: readonly string[];
  /** Hinweis, was zur Nutzung fehlt, oder {@code null}, wenn der Scraper einsatzbereit ist. */
  readonly missingConfiguration: string | null;
  /** Vom Anbieter verlangter Hinweis auf die Datenquelle. */
  readonly attribution?: Attribution | null;
}

/** Hinweis auf die Datenquelle mit Link, z. B. für TheTVDB. */
export interface Attribution {
  readonly text: string;
  readonly link: string;
}

/** Sammlung eines Films beim Anbieter, etwa eine TMDB-Sammlung. */
export interface CollectionReference {
  readonly providerId: string;
  readonly name: string;
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

/** Antwort von GET /api/v1/movies/{id}/scraper-proposal. */
export interface MovieScrapeProposal<M> {
  readonly metadata: M;
  readonly collection: CollectionReference | null;
}
