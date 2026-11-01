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

  /** Filmliste mit Filter, standardmäßig nach Titel sortiert. */
  queryMovies(
    filter: MovieFilter,
    page: number,
    size: number,
    sort: ListSort = TITLE_ORDER,
  ): Observable<MoviePage> {
    const params = withSort(new HttpParams().set('page', page).set('size', size), sort);
    return this.http.post<MoviePage>(`${MOVIES_URL}/query`, filter, { params });
  }

  /** IDs aller Filme, auf die der Filter passt. */
  queryMovieIds(filter: MovieFilter): Observable<number[]> {
    return this.http.post<number[]>(`${MOVIES_URL}/query/ids`, filter);
  }

  /** Startet die Stapel-Bearbeitung als Job. */
  batchEdit(edit: MovieBatchEdit): Observable<Job> {
    return this.http.post<Job>(`${MOVIES_URL}/batch`, edit);
  }

  /** Vorhandene Werte je Filterfeld für Auswahllisten. */
  filterValues(): Observable<MovieFilterValues> {
    return this.http.get<MovieFilterValues>(`${MOVIES_URL}/filter-values`);
  }

  findMovie(movieId: number): Observable<MovieDetails> {
    return this.http.get<MovieDetails>(`${MOVIES_URL}/${movieId}`);
  }

  metadataScrapers(): Observable<ScraperInfo[]> {
    return this.http.get<ScraperInfo[]>(METADATA_SCRAPERS_URL);
  }

  /** Sucht online; ohne Titel nimmt das Backend Titel und Jahr des Films. */
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

  /** Vorhandene Metadaten, überlagert mit den Daten des gewählten Treffers, und dessen Sammlung; speichert nichts. */
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

  /** Ordnet den Film dem Set seiner Sammlung zu, sofern das in den Einstellungen eingeschaltet ist. */
  assignCollection(
    movieId: number,
    collection: CollectionReference,
  ): Observable<{ assigned: boolean }> {
    return this.http.post<{ assigned: boolean }>(`${MOVIES_URL}/${movieId}/collection`, collection);
  }

  /** Speichert die Metadaten; das Backend schreibt sie zugleich in die NFO. */
  updateMetadata(movieId: number, metadata: MovieMetadata): Observable<MovieDetails> {
    return this.http.put<MovieDetails>(`${MOVIES_URL}/${movieId}/metadata`, metadata);
  }

  /** Holt für die Filme ohne Trailer den besten Vorschlag, als Job. */
  /** Aktualisiert Bewertungen und füllt leere Felder über Trakt und OMDb, als Job. */
  refreshRatings(movieIds: readonly number[]): Observable<Job> {
    return this.http.post<Job>(`${MOVIES_URL}/ratings/refresh`, { movieIds });
  }

  fillMissingThemes(movieIds: readonly number[]): Observable<Job> {
    return this.http.post<Job>(`${MOVIES_URL}/themes/fill`, { movieIds });
  }

  autoScrape(movieId: number): Observable<Job> {
    return this.http.post<Job>(`${MOVIES_URL}/${movieId}/auto-scrape`, null);
  }

  /** Ergänzt für die Auswahl alles Fehlende wie das Scrapen einer Quelle, als Job. */
  autoScrapeAll(movieIds: readonly number[]): Observable<Job> {
    return this.http.post<Job>(`${MOVIES_URL}/auto-scrape`, { movieIds });
  }

  fillMissingTrailers(movieIds: readonly number[]): Observable<Job> {
    return this.http.post<Job>(`${MOVIES_URL}/trailers/fill`, { movieIds });
  }
}
