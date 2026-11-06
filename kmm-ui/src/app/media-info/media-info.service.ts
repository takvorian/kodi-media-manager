import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Job } from '../jobs/job';
import { StreamDetails } from '../shared/stream-details';
import { ShowStreamDetailsResult, StreamDetailsScope } from './media-info';

export const MEDIA_INFO_URL = '/api/v1';

/** Reads technical details (codecs, resolution, tracks) with ffprobe. */
@Injectable({ providedIn: 'root' })
export class MediaInfoService {
  private readonly http = inject(HttpClient);

  /** Reads the movies or episodes of the source; runs as a job. */
  probeSource(sourceId: number, scope: StreamDetailsScope): Observable<Job> {
    return this.http.post<Job>(`${MEDIA_INFO_URL}/sources/${sourceId}/stream-details`, null, {
      params: { scope },
    });
  }

  probeMovie(movieId: number): Observable<StreamDetails> {
    return this.http.post<StreamDetails>(
      `${MEDIA_INFO_URL}/movies/${movieId}/stream-details`,
      null,
    );
  }

  probeEpisode(episodeId: number): Observable<StreamDetails> {
    return this.http.post<StreamDetails>(
      `${MEDIA_INFO_URL}/episodes/${episodeId}/stream-details`,
      null,
    );
  }

  /** Reads the movie after the online search; `null` if this is switched off in the settings. */
  probeMovieAfterScrape(movieId: number): Observable<StreamDetails | null> {
    return this.http.post<StreamDetails | null>(
      `${MEDIA_INFO_URL}/movies/${movieId}/stream-details/after-scrape`,
      null,
    );
  }

  /** Reads all episodes of a season again, including those with existing details, as a job. */
  probeSeason(showId: number, season: number): Observable<Job> {
    return this.http.post<Job>(
      `${MEDIA_INFO_URL}/tvshows/${showId}/seasons/${season}/stream-details`,
      null,
    );
  }

  /** Reads the episodes without details after the online search; `null` if this is switched off. */
  probeShowAfterScrape(showId: number): Observable<ShowStreamDetailsResult | null> {
    return this.http.post<ShowStreamDetailsResult | null>(
      `${MEDIA_INFO_URL}/tvshows/${showId}/stream-details/after-scrape`,
      null,
    );
  }
}
