import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Job } from '../jobs/job';
import { BackupHeader, BackupImportRequest } from './backup';

export const BACKUP_URL = '/api/v1/backup';

@Injectable({ providedIn: 'root' })
export class BackupService {
  private readonly http = inject(HttpClient);

  /** Downloads the backup; credentials only on explicit request. */
  export(credentials: boolean): Observable<HttpResponse<Blob>> {
    return this.http.post(`${BACKUP_URL}/export`, null, {
      params: { credentials },
      observe: 'response',
      responseType: 'blob',
    });
  }

  /** Uploads a backup and returns what it contains. */
  upload(file: File): Observable<BackupHeader> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<BackupHeader>(`${BACKUP_URL}/upload`, form);
  }

  /** Deletes the uploaded backup on the server. */
  discardUpload(): Observable<void> {
    return this.http.delete<void>(`${BACKUP_URL}/upload`);
  }

  /** Starts the import or dry run in the background and returns the started job. */
  startImport(request: BackupImportRequest): Observable<Job> {
    return this.http.post<Job>(`${BACKUP_URL}/import`, request);
  }
}
