import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Job } from '../jobs/job';
import { StreamDetails } from '../shared/stream-details';
import { ShowStreamDetailsResult, StreamDetailsScope } from './media-info';

export const MEDIA_INFO_URL = '/api/v1';

/** Liest technische Angaben (Codecs, Auflösung, Spuren) mit ffprobe aus. */
@Injectable({ providedIn: 'root' })
export class MediaInfoService {
  private readonly http = inject(HttpClient);

  /** Liest die Filme bzw. Episoden der Quelle aus; läuft als Job. */
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

  /** Liest den Film nach der Online-Suche aus; {@code null}, wenn das in den Einstellungen abgeschaltet ist. */
  probeMovieAfterScrape(movieId: number): Observable<StreamDetails | null> {
    return this.http.post<StreamDetails | null>(
      `${MEDIA_INFO_URL}/movies/${movieId}/stream-details/after-scrape`,
      null,
    );
  }

  /** Liest alle Episoden einer Staffel neu aus, auch solche mit vorhandenen Angaben, als Job. */
  probeSeason(showId: number, season: number): Observable<Job> {
    return this.http.post<Job>(
      `${MEDIA_INFO_URL}/tvshows/${showId}/seasons/${season}/stream-details`,
      null,
    );
  }

  /** Liest die Episoden ohne Angaben nach der Online-Suche aus; {@code null}, wenn das abgeschaltet ist. */
  probeShowAfterScrape(showId: number): Observable<ShowStreamDetailsResult | null> {
    return this.http.post<ShowStreamDetailsResult | null>(
      `${MEDIA_INFO_URL}/tvshows/${showId}/stream-details/after-scrape`,
      null,
    );
  }
}
