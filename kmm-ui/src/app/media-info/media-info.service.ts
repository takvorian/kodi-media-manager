import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Job } from '../jobs/job';
import { StreamDetails } from '../shared/stream-details';
import { StreamDetailsScope } from './media-info';

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
    return this.http.post<StreamDetails>(`${MEDIA_INFO_URL}/movies/${movieId}/stream-details`, null);
  }

  probeEpisode(episodeId: number): Observable<StreamDetails> {
    return this.http.post<StreamDetails>(`${MEDIA_INFO_URL}/episodes/${episodeId}/stream-details`, null);
  }
}
