import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { TvShowFilter, TvShowFilterValues } from '../filters/tvshow-filter';
import { Job } from '../jobs/job';
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

  /** Serienliste mit Filter, nach Titel sortiert. */
  queryShows(filter: TvShowFilter, page: number, size: number): Observable<TvShowPage> {
    const params = new HttpParams().set('page', page).set('size', size).set('sort', SORT_BY_TITLE);
    return this.http.post<TvShowPage>(`${TVSHOWS_URL}/query`, filter, { params });
  }

  /** IDs aller Serien, auf die der Filter passt. */
  queryShowIds(filter: TvShowFilter): Observable<number[]> {
    return this.http.post<number[]>(`${TVSHOWS_URL}/query/ids`, filter);
  }

  /** Startet die Stapel-Bearbeitung als Job. */
  batchEdit(edit: TvShowBatchEdit): Observable<Job> {
    return this.http.post<Job>(`${TVSHOWS_URL}/batch`, edit);
  }

  /** Holt für die Serien ohne Trailer den besten Vorschlag, als Job. */
  fillMissingThemes(showIds: readonly number[]): Observable<Job> {
    return this.http.post<Job>(`${TVSHOWS_URL}/themes/fill`, { showIds });
  }

  fillMissingTrailers(showIds: readonly number[]): Observable<Job> {
    return this.http.post<Job>(`${TVSHOWS_URL}/trailers/fill`, { showIds });
  }

  /** Vorhandene Werte je Filterfeld für Auswahllisten. */
  filterValues(): Observable<TvShowFilterValues> {
    return this.http.get<TvShowFilterValues>(`${TVSHOWS_URL}/filter-values`);
  }

  findShow(showId: number): Observable<TvShowDetails> {
    return this.http.get<TvShowDetails>(`${TVSHOWS_URL}/${showId}`);
  }

  /** Speichert die Metadaten; das Backend schreibt sie zugleich in die tvshow.nfo. */
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

  /** Ergänzt leere Angaben der Episoden aus dem Treffer und schreibt deren NFOs. */
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

  /** Speichert die Metadaten; das Backend schreibt sie zugleich in die NFO der Episode. */
  updateEpisode(episodeId: number, metadata: EpisodeMetadata): Observable<EpisodeDetails> {
    return this.http.put<EpisodeDetails>(`${EPISODES_URL}/${episodeId}/metadata`, metadata);
  }
}
