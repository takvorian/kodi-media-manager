import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Job } from '../jobs/job';
import {
  KodiConnectionTest,
  KodiHost,
  KodiHostCommand,
  KodiPlaybackResult,
  KodiSyncResult,
} from './kodi';

export const KODI_URL = '/api/v1/kodi';
export const KODI_HOSTS_URL = `${KODI_URL}/hosts`;

@Injectable({ providedIn: 'root' })
export class KodiService {
  private readonly http = inject(HttpClient);

  hosts(): Observable<KodiHost[]> {
    return this.http.get<KodiHost[]>(KODI_HOSTS_URL);
  }

  create(host: KodiHostCommand): Observable<KodiHost> {
    return this.http.post<KodiHost>(KODI_HOSTS_URL, host);
  }

  update(hostId: number, host: KodiHostCommand): Observable<KodiHost> {
    return this.http.put<KodiHost>(`${KODI_HOSTS_URL}/${hostId}`, host);
  }

  delete(hostId: number): Observable<void> {
    return this.http.delete<void>(`${KODI_HOSTS_URL}/${hostId}`);
  }

  testConnection(hostId: number): Observable<KodiConnectionTest> {
    return this.http.post<KodiConnectionTest>(`${KODI_HOSTS_URL}/${hostId}/test`, null);
  }

  /** Refreshes the movie on all Kodi devices. */
  refreshMovie(movieId: number): Observable<KodiSyncResult[]> {
    return this.http.post<KodiSyncResult[]>(`${KODI_URL}/movies/${movieId}/refresh`, null);
  }

  /** Refreshes the selection on all Kodi devices; runs as a job. */
  refreshMovies(movieIds: readonly number[]): Observable<Job> {
    return this.http.post<Job>(`${KODI_URL}/movies/refresh`, { movieIds });
  }

  /** Refreshes the selection (without episodes) on all Kodi devices; runs as a job. */
  refreshTvShows(showIds: readonly number[]): Observable<Job> {
    return this.http.post<Job>(`${KODI_URL}/tvshows/refresh`, { showIds });
  }

  /** Refreshes the TV show (without episodes) on all Kodi devices. */
  refreshTvShow(showId: number): Observable<KodiSyncResult[]> {
    return this.http.post<KodiSyncResult[]>(`${KODI_URL}/tvshows/${showId}/refresh`, null);
  }

  /** Refreshes the episode on all Kodi devices. */
  refreshEpisode(episodeId: number): Observable<KodiSyncResult[]> {
    return this.http.post<KodiSyncResult[]>(`${KODI_URL}/episodes/${episodeId}/refresh`, null);
  }

  /** Lets all Kodi devices scan the directory of the source for new movies or TV shows. */
  scanSource(sourceId: number): Observable<KodiSyncResult[]> {
    return this.http.post<KodiSyncResult[]>(`${KODI_URL}/sources/${sourceId}/scan`, null);
  }

  /** Fetches the watched status of all movies or episodes of the source from Kodi; runs as a job. */
  importSourcePlayback(sourceId: number): Observable<Job> {
    return this.http.post<Job>(`${KODI_URL}/sources/${sourceId}/playback`, null);
  }

  /** Fetches the watched status of the movie from Kodi. */
  importMoviePlayback(movieId: number): Observable<KodiPlaybackResult> {
    return this.http.post<KodiPlaybackResult>(`${KODI_URL}/movies/${movieId}/playback`, null);
  }

  /** Fetches the watched status of the episode from Kodi. */
  importEpisodePlayback(episodeId: number): Observable<KodiPlaybackResult> {
    return this.http.post<KodiPlaybackResult>(`${KODI_URL}/episodes/${episodeId}/playback`, null);
  }
}
