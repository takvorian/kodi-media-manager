import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Job } from '../jobs/job';
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
}
