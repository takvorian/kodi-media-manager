import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { KodiConnectionTest, KodiHost, KodiHostCommand } from './kodi';

export const KODI_HOSTS_URL = '/api/v1/kodi/hosts';

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
}
