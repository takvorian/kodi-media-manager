import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Job } from '../jobs/job';
import { ArtworkCandidates, ArtworkInfo, ArtworkSource, versioned } from '../movies/artwork';
import { MovieSetData, MovieSetDetails, MovieSetSummary } from './movie-set';

export const MOVIE_SETS_URL = '/api/v1/moviesets';

@Injectable({ providedIn: 'root' })
export class MovieSetService {
  private readonly http = inject(HttpClient);

  findAll(): Observable<MovieSetSummary[]> {
    return this.http.get<MovieSetSummary[]>(MOVIE_SETS_URL);
  }

  find(setId: number): Observable<MovieSetDetails> {
    return this.http.get<MovieSetDetails>(`${MOVIE_SETS_URL}/${setId}`);
  }

  create(data: MovieSetData): Observable<MovieSetDetails> {
    return this.http.post<MovieSetDetails>(MOVIE_SETS_URL, data);
  }

  update(setId: number, data: MovieSetData): Observable<Job> {
    return this.http.put<Job>(`${MOVIE_SETS_URL}/${setId}`, data);
  }

  delete(setId: number): Observable<Job> {
    return this.http.delete<Job>(`${MOVIE_SETS_URL}/${setId}`);
  }

  addMovies(setId: number, movieIds: readonly number[]): Observable<Job> {
    return this.http.post<Job>(`${MOVIE_SETS_URL}/${setId}/movies`, { movieIds });
  }

  removeMovies(setId: number, movieIds: readonly number[]): Observable<Job> {
    return this.http.post<Job>(`${MOVIE_SETS_URL}/${setId}/movies/remove`, { movieIds });
  }

  /** Bilder eines Sets; gespeichert wird in den Ablagen aus den Einstellungen. */
  artwork(setId: number): ArtworkSource {
    const baseUrl = `${MOVIE_SETS_URL}/${setId}/artwork`;
    return {
      findAll: () => this.http.get<ArtworkInfo[]>(baseUrl),
      imageUrl: (artwork) => versioned(`${baseUrl}/${artwork.type}/image`, artwork),
      candidates: (type) => this.http.get<ArtworkCandidates>(`${baseUrl}/${type}/candidates`),
      apply: (type, url) => this.http.put<ArtworkInfo>(`${baseUrl}/${type}`, { url }),
      remove: (type) => this.http.delete<void>(`${baseUrl}/${type}`),
    };
  }
}
