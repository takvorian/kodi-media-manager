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

  /** Aktualisiert den Film auf allen Kodi-Geräten. */
  refreshMovie(movieId: number): Observable<KodiSyncResult[]> {
    return this.http.post<KodiSyncResult[]>(`${KODI_URL}/movies/${movieId}/refresh`, null);
  }

  /** Aktualisiert die Auswahl auf allen Kodi-Geräten; läuft als Job. */
  refreshMovies(movieIds: readonly number[]): Observable<Job> {
    return this.http.post<Job>(`${KODI_URL}/movies/refresh`, { movieIds });
  }

  /** Aktualisiert die Auswahl (ohne Episoden) auf allen Kodi-Geräten; läuft als Job. */
  refreshTvShows(showIds: readonly number[]): Observable<Job> {
    return this.http.post<Job>(`${KODI_URL}/tvshows/refresh`, { showIds });
  }

  /** Aktualisiert die Serie (ohne Episoden) auf allen Kodi-Geräten. */
  refreshTvShow(showId: number): Observable<KodiSyncResult[]> {
    return this.http.post<KodiSyncResult[]>(`${KODI_URL}/tvshows/${showId}/refresh`, null);
  }

  /** Aktualisiert die Episode auf allen Kodi-Geräten. */
  refreshEpisode(episodeId: number): Observable<KodiSyncResult[]> {
    return this.http.post<KodiSyncResult[]>(`${KODI_URL}/episodes/${episodeId}/refresh`, null);
  }

  /** Lässt alle Kodi-Geräte das Verzeichnis der Quelle nach neuen Filmen bzw. Serien durchsuchen. */
  scanSource(sourceId: number): Observable<KodiSyncResult[]> {
    return this.http.post<KodiSyncResult[]>(`${KODI_URL}/sources/${sourceId}/scan`, null);
  }

  /** Holt den Gesehen-Status aller Filme bzw. Episoden der Quelle aus Kodi; läuft als Job. */
  importSourcePlayback(sourceId: number): Observable<Job> {
    return this.http.post<Job>(`${KODI_URL}/sources/${sourceId}/playback`, null);
  }

  /** Holt den Gesehen-Status des Films aus Kodi. */
  importMoviePlayback(movieId: number): Observable<KodiPlaybackResult> {
    return this.http.post<KodiPlaybackResult>(`${KODI_URL}/movies/${movieId}/playback`, null);
  }

  /** Holt den Gesehen-Status der Episode aus Kodi. */
  importEpisodePlayback(episodeId: number): Observable<KodiPlaybackResult> {
    return this.http.post<KodiPlaybackResult>(`${KODI_URL}/episodes/${episodeId}/playback`, null);
  }
}
