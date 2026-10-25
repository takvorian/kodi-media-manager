import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { MoviePage, MovieQuery } from './movie';

export const MOVIES_URL = '/api/v1/movies';
const SORT_BY_TITLE = 'title,asc';

@Injectable({ providedIn: 'root' })
export class MovieService {
  private readonly http = inject(HttpClient);

  findMovies(query: MovieQuery): Observable<MoviePage> {
    let params = new HttpParams().set('page', query.page).set('size', query.size).set('sort', SORT_BY_TITLE);
    const search = query.search.trim();
    if (search) {
      params = params.set('search', search);
    }
    return this.http.get<MoviePage>(MOVIES_URL, { params });
  }
}
