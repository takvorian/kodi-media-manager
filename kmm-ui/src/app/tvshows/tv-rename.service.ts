import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Job } from '../jobs/job';
import { TvRenamePatterns, TvShowRenamePlan } from './tv-rename';

@Injectable({ providedIn: 'root' })
export class TvRenameService {
  private readonly http = inject(HttpClient);

  /** What renaming the TV shows of a source would change; changes nothing. */
  previewSource(sourceId: number, patterns: TvRenamePatterns): Observable<TvShowRenamePlan[]> {
    return this.http.post<TvShowRenamePlan[]>(
      `/api/v1/sources/${sourceId}/tv-rename/preview`,
      patterns,
    );
  }

  /** Renames the selected TV shows of the source in the background. */
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
