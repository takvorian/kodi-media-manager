import { LocalizedText } from '../i18n/localized-text';

/** Response of GET /api/v1/scrapers/metadata. */
export interface ScraperInfo {
  readonly id: string;
  readonly displayName: string;
  readonly mediaTypes: readonly string[];
  /** Note on what is missing for use, or {@code null} if the scraper is ready. */
  readonly missingConfiguration: LocalizedText | null;
  /** Attribution of the data source required by the provider. */
  readonly attribution?: Attribution | null;
}

/** Attribution of the data source with a link, e.g. for TheTVDB. */
export interface Attribution {
  readonly text: LocalizedText;
  readonly link: string;
}

/** Collection of a movie at the provider, such as a TMDB collection. */
export interface CollectionReference {
  readonly providerId: string;
  readonly name: string;
}

/** Search result of a scraper. */
export interface ScrapeCandidate {
  readonly providerId: string;
  readonly title: string;
  readonly originalTitle: string | null;
  readonly year: number | null;
  readonly overview: string | null;
  readonly posterUrl: string | null;
}

/** Response of GET /api/v1/movies/{id}/scraper-proposal. */
export interface MovieScrapeProposal<M> {
  readonly metadata: M;
  readonly collection: CollectionReference | null;
}
