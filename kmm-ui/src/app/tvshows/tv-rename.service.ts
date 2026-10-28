import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Job } from '../jobs/job';
import { TvRenamePatterns, TvShowRenamePlan } from './tv-rename';

@Injectable({ providedIn: 'root' })
export class TvRenameService {
  private readonly http = inject(HttpClient);

  /** Was das Umbenennen der Serien einer Quelle ändern würde; ändert nichts. */
  previewSource(sourceId: number, patterns: TvRenamePatterns): Observable<TvShowRenamePlan[]> {
    return this.http.post<TvShowRenamePlan[]>(
      `/api/v1/sources/${sourceId}/tv-rename/preview`,
      patterns,
    );
  }

  /** Benennt die ausgewählten Serien der Quelle im Hintergrund um. */
  renameSource(
    sourceId: number,
    patterns: TvRenamePatterns,
    showIds: readonly number[],
  ): Observable<Job> {
    return this.http.post<Job>(`/api/v1/sources/${sourceId}/tv-rename`, { patterns, showIds });
  }

  previewShow(showId: number, patterns: TvRenamePatterns): Observable<TvShowRenamePlan> {
    return this.http.post<TvShowRenamePlan>(`/api/v1/tvshows/${showId}/rename/preview`, patterns);
  }

  renameShow(showId: number, patterns: TvRenamePatterns): Observable<TvShowRenamePlan> {
    return this.http.post<TvShowRenamePlan>(`/api/v1/tvshows/${showId}/rename`, patterns);
  }
}
