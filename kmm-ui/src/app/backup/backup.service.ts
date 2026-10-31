import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Job } from '../jobs/job';
import { BackupHeader, BackupImportRequest } from './backup';

export const BACKUP_URL = '/api/v1/backup';

@Injectable({ providedIn: 'root' })
export class BackupService {
  private readonly http = inject(HttpClient);

  /** Lädt die Sicherung herunter; Zugangsdaten nur auf ausdrücklichen Wunsch. */
  export(credentials: boolean): Observable<HttpResponse<Blob>> {
    return this.http.post(`${BACKUP_URL}/export`, null, {
      params: { credentials },
      observe: 'response',
      responseType: 'blob',
    });
  }

  /** Lädt eine Sicherung hoch und liefert, was sie enthält. */
  upload(file: File): Observable<BackupHeader> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<BackupHeader>(`${BACKUP_URL}/upload`, form);
  }

  /** Löscht die hochgeladene Sicherung auf dem Server. */
  discardUpload(): Observable<void> {
    return this.http.delete<void>(`${BACKUP_URL}/upload`);
  }

  /** Startet Import oder Probelauf im Hintergrund und liefert den gestarteten Job. */
  startImport(request: BackupImportRequest): Observable<Job> {
    return this.http.post<Job>(`${BACKUP_URL}/import`, request);
  }
}
