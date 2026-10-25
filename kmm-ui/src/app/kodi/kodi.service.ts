import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { KodiConnectionTest, KodiHost, KodiHostCommand, KodiSyncResult } from './kodi';

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

  /** Lässt alle Kodi-Geräte das Verzeichnis der Quelle nach neuen Filmen durchsuchen. */
  scanSource(sourceId: number): Observable<KodiSyncResult[]> {
    return this.http.post<KodiSyncResult[]>(`${KODI_URL}/sources/${sourceId}/scan`, null);
  }
}
