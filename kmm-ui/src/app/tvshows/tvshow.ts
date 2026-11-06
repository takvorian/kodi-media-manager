import { translate } from '@jsverse/transloco';

import { StreamDetails } from '../shared/stream-details';

export interface TvShowSummary {
  readonly id: number;
  readonly sourceId: number;
  readonly title: string;
  readonly year: number | null;
  readonly directoryPath: string;
  readonly episodeCount: number;
  readonly addedAt: string;
}

/** Response of GET /api/v1/tvshows. */
export interface TvShowPage {
  readonly content: readonly TvShowSummary[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}

export interface TvShowQuery {
  readonly search: string;
  readonly page: number;
  readonly size: number;
}

/** Editable metadata of a TV show, matches the content of the tvshow.nfo. */
export interface TvShowMetadata {
  readonly title: string;
  readonly originalTitle: string | null;
  readonly sortTitle: string | null;
  readonly year: number | null;
  readonly premiered: string | null;
  readonly plot: string | null;
  readonly runtime: number | null;
  readonly mpaa: string | null;
  readonly status: string | null;
  readonly genres: readonly string[];
  readonly studios: readonly string[];
  readonly tags: readonly string[];
  readonly tvdbId: string | null;
  readonly tmdbId: string | null;
  readonly imdbId: string | null;
  readonly userRating: number | null;
}

export interface TvActor {
  readonly name: string;
  readonly role: string | null;
  readonly thumb: string | null;
}

export interface TvRating {
  readonly source: string;
  readonly value: number;
  readonly max: number | null;
  readonly votes: number | null;
  readonly defaultRating: boolean;
}

export interface TvShowExtras {
  readonly actors: readonly TvActor[];
  readonly ratings: readonly TvRating[];
  /** Season titles by season number. */
  readonly seasonNames: Readonly<Record<string, string>>;
}

export interface Season {
  readonly number: number;
  readonly title: string | null;
  readonly episodeCount: number;
}

export interface EpisodeSummary {
  readonly id: number;
  readonly season: number;
  readonly episode: number;
  readonly title: string;
  readonly aired: string | null;
  readonly filePath: string;
  readonly playCount: number | null;
}

export interface NfoInfo {
  readonly path: string;
  readonly exists: boolean;
  readonly modifiedAt: string | null;
}

export interface TvShowDetails {
  readonly id: number;
  readonly sourceId: number;
  readonly directoryPath: string;
  readonly addedAt: string;
  readonly metadata: TvShowMetadata;
  readonly extras: TvShowExtras;
  readonly seasons: readonly Season[];
  readonly episodes: readonly EpisodeSummary[];
  readonly nfo: NfoInfo;
  /** Trailer link of the tvshow.nfo that Kodi plays. */
  readonly trailer: string | null;
  /** Own language, also for the episodes; without one, that of the source or the settings applies. */
  readonly language: string | null;
}

/** Editable metadata of an episode; season and episode are part of the file name. */
export interface EpisodeMetadata {
  readonly title: string;
  readonly originalTitle: string | null;
  readonly plot: string | null;
  readonly aired: string | null;
  readonly runtime: number | null;
  readonly tvdbId: string | null;
  readonly tmdbId: string | null;
  readonly imdbId: string | null;
  readonly userRating: number | null;
  /** Video source, e.g. dvd. */
  readonly videoSource: string | null;
}

export interface EpisodeExtras {
  readonly directors: readonly string[];
  readonly writers: readonly string[];
  readonly actors: readonly TvActor[];
  readonly ratings: readonly TvRating[];
  readonly playCount: number | null;
  readonly lastPlayed: string | null;
  readonly streams: StreamDetails;
}

export interface EpisodeDetails {
  readonly id: number;
  readonly tvShowId: number;
  readonly showTitle: string;
  readonly filePath: string;
  readonly season: number;
  readonly episode: number;
  readonly addedAt: string;
  readonly metadata: EpisodeMetadata;
  readonly extras: EpisodeExtras;
  readonly nfo: NfoInfo;
}

/** Season and episode as in Kodi, e.g. “S01E04”. */
export function episodeCode(season: number, episode: number): string {
  return `S${String(season).padStart(2, '0')}E${String(episode).padStart(2, '0')}`;
}

/** “Season 1”, “Specials” for season 0, with its title if there is one; in the active UI language. */
export function seasonLabel(season: Season): string {
  const name =
    season.number === 0
      ? translate('tvshows.specials')
      : translate('tvshows.season', { number: season.number });
  return season.title && season.title !== name
    ? translate('tvshows.seasonWithTitle', { season: name, title: season.title })
    : name;
}

/** Proposal from the online search for the form of the TV show. */
export interface TvShowProposal {
  readonly metadata: TvShowMetadata;
  /** Episodes of the library that the provider knows and completes on adopting. */
  readonly matchedEpisodes: number;
  readonly providerEpisodes: number;
}

/** Result of adopting episode data. */
export interface EpisodeFillResult {
  readonly updated: number;
  readonly unchanged: number;
  readonly unmatched: number;
  readonly failedFiles: readonly string[];
}

/** Episode the provider knows but for which there is no file. */
export interface MissingEpisode {
  readonly season: number;
  readonly episode: number;
  readonly title: string | null;
  readonly aired: string | null;
}
