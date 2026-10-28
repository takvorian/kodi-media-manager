import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { MovieFilter } from './movie-filter';

export const FILTERS_URL = '/api/v1/filters';

export type FilterMediaType = 'MOVIE' | 'TV_SHOW';

/** Antwort von GET /api/v1/filters. */
export interface SavedFilter {
  readonly id: number;
  readonly mediaType: FilterMediaType;
  readonly name: string;
  readonly definition: MovieFilter;
}

@Injectable({ providedIn: 'root' })
export class SavedFilterService {
  private readonly http = inject(HttpClient);

  findAll(mediaType: FilterMediaType): Observable<SavedFilter[]> {
    return this.http.get<SavedFilter[]>(FILTERS_URL, {
      params: new HttpParams().set('mediaType', mediaType),
    });
  }

  create(
    mediaType: FilterMediaType,
    name: string,
    definition: MovieFilter,
  ): Observable<SavedFilter> {
    return this.http.post<SavedFilter>(FILTERS_URL, { mediaType, name, definition });
  }

  update(
    id: number,
    mediaType: FilterMediaType,
    name: string,
    definition: MovieFilter,
  ): Observable<SavedFilter> {
    return this.http.put<SavedFilter>(`${FILTERS_URL}/${id}`, { mediaType, name, definition });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${FILTERS_URL}/${id}`);
  }
}
