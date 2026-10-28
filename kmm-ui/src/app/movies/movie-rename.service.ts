import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Job } from '../jobs/job';
import { MovieRenamePatterns, MovieRenamePlan } from './movie-rename';

@Injectable({ providedIn: 'root' })
export class MovieRenameService {
  private readonly http = inject(HttpClient);

  /** Was das Umbenennen der Filme einer Quelle ändern würde; ändert nichts. */
  previewSource(sourceId: number, patterns: MovieRenamePatterns): Observable<MovieRenamePlan[]> {
    return this.http.post<MovieRenamePlan[]>(
      `/api/v1/sources/${sourceId}/movie-rename/preview`,
      patterns,
    );
  }

  /** Benennt die ausgewählten Filme der Quelle im Hintergrund um. */
  renameSource(
    sourceId: number,
    patterns: MovieRenamePatterns,
    movieIds: readonly number[],
  ): Observable<Job> {
    return this.http.post<Job>(`/api/v1/sources/${sourceId}/movie-rename`, { patterns, movieIds });
  }

  previewMovie(movieId: number, patterns: MovieRenamePatterns): Observable<MovieRenamePlan> {
    return this.http.post<MovieRenamePlan>(`/api/v1/movies/${movieId}/rename/preview`, patterns);
  }

  renameMovie(movieId: number, patterns: MovieRenamePatterns): Observable<MovieRenamePlan> {
    return this.http.post<MovieRenamePlan>(`/api/v1/movies/${movieId}/rename`, patterns);
  }
}
