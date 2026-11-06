import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { MovieFilter, MovieFilterValues } from '../filters/movie-filter';
import { Job } from '../jobs/job';
import { ListSort, SORT_BY_TITLE as TITLE_ORDER, withSort } from '../shared/list-sort';
import { MovieDetails, MovieMetadata, MoviePage, MovieQuery } from './movie';
import { MovieBatchEdit } from './movie-batch';
import { CollectionReference, MovieScrapeProposal, ScrapeCandidate, ScraperInfo } from './scraper';

export const MOVIES_URL = '/api/v1/movies';
export const METADATA_SCRAPERS_URL = '/api/v1/scrapers/metadata';
const SORT_BY_TITLE = 'title,asc';

@Injectable({ providedIn: 'root' })
export class MovieService {
  private readonly http = inject(HttpClient);

  findMovies(query: MovieQuery): Observable<MoviePage> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size)
      .set('sort', SORT_BY_TITLE);
    const search = query.search.trim();
    if (search) {
      params = params.set('search', search);
    }
    return this.http.get<MoviePage>(MOVIES_URL, { params });
  }

  /** Movie list with filter, sorted by title by default. */
  queryMovies(
    filter: MovieFilter,
    page: number,
    size: number,
    sort: ListSort = TITLE_ORDER,
  ): Observable<MoviePage> {
    const params = withSort(new HttpParams().set('page', page).set('size', size), sort);
    return this.http.post<MoviePage>(`${MOVIES_URL}/query`, filter, { params });
  }

  /** IDs of all movies matching the filter. */
  queryMovieIds(filter: MovieFilter): Observable<number[]> {
    return this.http.post<number[]>(`${MOVIES_URL}/query/ids`, filter);
  }

  /** Starts the batch edit as a job. */
  batchEdit(edit: MovieBatchEdit): Observable<Job> {
    return this.http.post<Job>(`${MOVIES_URL}/batch`, edit);
  }

  /** Existing values per filter field for selection lists. */
  filterValues(): Observable<MovieFilterValues> {
    return this.http.get<MovieFilterValues>(`${MOVIES_URL}/filter-values`);
  }

  findMovie(movieId: number): Observable<MovieDetails> {
    return this.http.get<MovieDetails>(`${MOVIES_URL}/${movieId}`);
  }

  metadataScrapers(): Observable<ScraperInfo[]> {
    return this.http.get<ScraperInfo[]>(METADATA_SCRAPERS_URL);
  }

  /** Searches online; without a title the backend uses the title and year of the movie. */
  searchOnline(
    movieId: number,
    scraper: string,
    title: string,
    year: number | null,
  ): Observable<ScrapeCandidate[]> {
    let params = new HttpParams().set('scraper', scraper);
    if (title.trim()) {
      params = params.set('title', title.trim());
      if (year !== null) {
        params = params.set('year', year);
      }
    }
    return this.http.get<ScrapeCandidate[]>(`${MOVIES_URL}/${movieId}/scraper-search`, { params });
  }

  /** Existing metadata overlaid with the data of the chosen result, and its collection; saves nothing. */
  proposal(
    movieId: number,
    scraper: string,
    providerId: string,
  ): Observable<MovieScrapeProposal<MovieMetadata>> {
    const params = new HttpParams().set('scraper', scraper).set('providerId', providerId);
    return this.http.get<MovieScrapeProposal<MovieMetadata>>(
      `${MOVIES_URL}/${movieId}/scraper-proposal`,
      { params },
    );
  }

  /** Assigns the movie to the set of its collection, if switched on in the settings. */
  assignCollection(
    movieId: number,
    collection: CollectionReference,
  ): Observable<{ assigned: boolean }> {
    return this.http.post<{ assigned: boolean }>(`${MOVIES_URL}/${movieId}/collection`, collection);
  }

  /** Saves the metadata; the backend writes them to the NFO at the same time. */
  updateMetadata(movieId: number, metadata: MovieMetadata): Observable<MovieDetails> {
    return this.http.put<MovieDetails>(`${MOVIES_URL}/${movieId}/metadata`, metadata);
  }

  /** Fetches the best proposal for the movies without a trailer, as a job. */
  /** Refreshes ratings and fills empty fields via Trakt and OMDb, as a job. */
  refreshRatings(movieIds: readonly number[]): Observable<Job> {
    return this.http.post<Job>(`${MOVIES_URL}/ratings/refresh`, { movieIds });
  }

  fillMissingThemes(movieIds: readonly number[]): Observable<Job> {
    return this.http.post<Job>(`${MOVIES_URL}/themes/fill`, { movieIds });
  }

  autoScrape(movieId: number): Observable<Job> {
    return this.http.post<Job>(`${MOVIES_URL}/${movieId}/auto-scrape`, null);
  }

  /** Adds everything missing for the selection like scraping a source does, as a job. */
  changeLanguage(
    movieIds: readonly number[],
    language: string | null,
    refetchTexts: boolean,
  ): Observable<Job> {
    return this.http.post<Job>(`${MOVIES_URL}/language`, { movieIds, language, refetchTexts });
  }

  autoScrapeAll(movieIds: readonly number[]): Observable<Job> {
    return this.http.post<Job>(`${MOVIES_URL}/auto-scrape`, { movieIds });
  }

  fillMissingTrailers(movieIds: readonly number[]): Observable<Job> {
    return this.http.post<Job>(`${MOVIES_URL}/trailers/fill`, { movieIds });
  }
}
