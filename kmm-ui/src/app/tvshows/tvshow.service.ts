import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { TvShowFilter, TvShowFilterValues } from '../filters/tvshow-filter';
import { Job } from '../jobs/job';
import { ListSort, SORT_BY_TITLE as TITLE_ORDER, withSort } from '../shared/list-sort';
import { RemovalPlan } from '../shared/removal';
import { ScrapeCandidate, ScraperInfo } from '../movies/scraper';
import {
  EpisodeDetails,
  EpisodeFillResult,
  EpisodeMetadata,
  MissingEpisode,
  TvShowDetails,
  TvShowMetadata,
  TvShowPage,
  TvShowProposal,
  TvShowQuery,
} from './tvshow';
import { TvShowBatchEdit } from './tvshow-batch';

export const TVSHOWS_URL = '/api/v1/tvshows';
export const EPISODES_URL = '/api/v1/episodes';
export const TV_SHOW_SCRAPERS_URL = '/api/v1/scrapers/tvshows';
const SORT_BY_TITLE = 'title,asc';

@Injectable({ providedIn: 'root' })
export class TvShowService {
  private readonly http = inject(HttpClient);

  findShows(query: TvShowQuery): Observable<TvShowPage> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size)
      .set('sort', SORT_BY_TITLE);
    const search = query.search.trim();
    if (search) {
      params = params.set('search', search);
    }
    return this.http.get<TvShowPage>(TVSHOWS_URL, { params });
  }

  /** TV show list with filter, sorted by title by default. */
  queryShows(
    filter: TvShowFilter,
    page: number,
    size: number,
    sort: ListSort = TITLE_ORDER,
  ): Observable<TvShowPage> {
    const params = withSort(new HttpParams().set('page', page).set('size', size), sort);
    return this.http.post<TvShowPage>(`${TVSHOWS_URL}/query`, filter, { params });
  }

  /** IDs of all TV shows the filter matches. */
  queryShowIds(filter: TvShowFilter): Observable<number[]> {
    return this.http.post<number[]>(`${TVSHOWS_URL}/query/ids`, filter);
  }

  /** Starts the batch edit as a job. */
  batchEdit(edit: TvShowBatchEdit): Observable<Job> {
    return this.http.post<Job>(`${TVSHOWS_URL}/batch`, edit);
  }

  /** Updates ratings and fills empty fields via Trakt and OMDb, as a job. */
  refreshRatings(showIds: readonly number[]): Observable<Job> {
    return this.http.post<Job>(`${TVSHOWS_URL}/ratings/refresh`, { showIds });
  }

  fillMissingThemes(showIds: readonly number[]): Observable<Job> {
    return this.http.post<Job>(`${TVSHOWS_URL}/themes/fill`, { showIds });
  }

  autoScrape(showId: number): Observable<Job> {
    return this.http.post<Job>(`${TVSHOWS_URL}/${showId}/auto-scrape`, null);
  }

  /** Scrapes one season; with `overwrite` the titles and texts of its episodes are replaced. */
  autoScrapeSeason(showId: number, season: number, overwrite: boolean): Observable<Job> {
    return this.http.post<Job>(`${TVSHOWS_URL}/${showId}/seasons/${season}/auto-scrape`, null, {
      params: { overwrite },
    });
  }

  /** Adds everything missing for the selection like scraping a source does, as a job. */
  changeLanguage(
    showIds: readonly number[],
    language: string | null,
    refetchTexts: boolean,
  ): Observable<Job> {
    return this.http.post<Job>(`${TVSHOWS_URL}/language`, { showIds, language, refetchTexts });
  }

  autoScrapeAll(showIds: readonly number[]): Observable<Job> {
    return this.http.post<Job>(`${TVSHOWS_URL}/auto-scrape`, { showIds });
  }

  /** Gets the best proposal for the TV shows without a trailer, as a job. */
  fillMissingTrailers(showIds: readonly number[]): Observable<Job> {
    return this.http.post<Job>(`${TVSHOWS_URL}/trailers/fill`, { showIds });
  }

  /** Existing values per filter field for drop-down lists. */
  filterValues(): Observable<TvShowFilterValues> {
    return this.http.get<TvShowFilterValues>(`${TVSHOWS_URL}/filter-values`);
  }

  findShow(showId: number): Observable<TvShowDetails> {
    return this.http.get<TvShowDetails>(`${TVSHOWS_URL}/${showId}`);
  }

  /** Saves the metadata; the backend writes them to the tvshow.nfo at the same time. */
  updateShow(showId: number, metadata: TvShowMetadata): Observable<TvShowDetails> {
    return this.http.put<TvShowDetails>(`${TVSHOWS_URL}/${showId}/metadata`, metadata);
  }

  tvShowScrapers(): Observable<ScraperInfo[]> {
    return this.http.get<ScraperInfo[]>(TV_SHOW_SCRAPERS_URL);
  }

  searchOnline(
    showId: number,
    scraper: string,
    title: string,
    year: number | null,
  ): Observable<ScrapeCandidate[]> {
    let params = new HttpParams().set('scraper', scraper).set('title', title);
    if (year !== null) {
      params = params.set('year', year);
    }
    return this.http.get<ScrapeCandidate[]>(`${TVSHOWS_URL}/${showId}/scraper-search`, { params });
  }

  proposal(showId: number, scraper: string, providerId: string): Observable<TvShowProposal> {
    const params = new HttpParams().set('scraper', scraper).set('providerId', providerId);
    return this.http.get<TvShowProposal>(`${TVSHOWS_URL}/${showId}/scraper-proposal`, { params });
  }

  /** Fills empty details of the episodes from the match and writes their NFOs. */
  fillEpisodes(showId: number, scraper: string, providerId: string): Observable<EpisodeFillResult> {
    const params = new HttpParams().set('scraper', scraper).set('providerId', providerId);
    return this.http.post<EpisodeFillResult>(`${TVSHOWS_URL}/${showId}/scraper-episodes`, null, {
      params,
    });
  }

  missingEpisodes(showId: number, scraper: string): Observable<MissingEpisode[]> {
    const params = new HttpParams().set('scraper', scraper);
    return this.http.get<MissingEpisode[]>(`${TVSHOWS_URL}/${showId}/missing-episodes`, { params });
  }

  findEpisode(episodeId: number): Observable<EpisodeDetails> {
    return this.http.get<EpisodeDetails>(`${EPISODES_URL}/${episodeId}`);
  }

  /** Saves the metadata; the backend writes them to the NFO of the episode at the same time. */
  updateEpisode(episodeId: number, metadata: EpisodeMetadata): Observable<EpisodeDetails> {
    return this.http.put<EpisodeDetails>(`${EPISODES_URL}/${episodeId}/metadata`, metadata);
  }

  /** What removing the episode would comprise, with a multi-episode file all its episodes; changes nothing. */
  episodeRemovalPlan(episodeId: number): Observable<RemovalPlan> {
    return this.http.get<RemovalPlan>(`${EPISODES_URL}/${episodeId}/removal`);
  }

  /** Removes the episode from the library, with `deleteFiles` also its files for good, as a job. */
  removeEpisode(episodeId: number, deleteFiles: boolean): Observable<Job> {
    return this.http.post<Job>(`${EPISODES_URL}/${episodeId}/removal`, { deleteFiles });
  }

  seasonRemovalPlan(showId: number, season: number): Observable<RemovalPlan> {
    return this.http.get<RemovalPlan>(`${TVSHOWS_URL}/${showId}/seasons/${season}/removal`);
  }

  /** Removes all episodes of the season, with `deleteFiles` also their files and the season artwork. */
  removeSeason(showId: number, season: number, deleteFiles: boolean): Observable<Job> {
    return this.http.post<Job>(`${TVSHOWS_URL}/${showId}/seasons/${season}/removal`, {
      deleteFiles,
    });
  }

  showRemovalPlan(showId: number): Observable<RemovalPlan> {
    return this.http.get<RemovalPlan>(`${TVSHOWS_URL}/${showId}/removal`);
  }

  /** Removes the TV show with all episodes, with `deleteFiles` also its whole folder. */
  removeShow(showId: number, deleteFiles: boolean): Observable<Job> {
    return this.http.post<Job>(`${TVSHOWS_URL}/${showId}/removal`, { deleteFiles });
  }
}
