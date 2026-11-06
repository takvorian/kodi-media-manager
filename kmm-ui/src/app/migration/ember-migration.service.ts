import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Job } from '../jobs/job';
import { EmberImportRequest, EmberInspection } from './ember-migration';

export const EMBER_MIGRATION_URL = '/api/v1/migrations/ember';

@Injectable({ providedIn: 'root' })
export class EmberMigrationService {
  private readonly http = inject(HttpClient);

  /** Uploads the profile files and returns the analysis of the profile. */
  upload(files: readonly File[]): Observable<EmberInspection> {
    const form = new FormData();
    files.forEach((file) => form.append('files', file, file.name));
    return this.http.post<EmberInspection>(`${EMBER_MIGRATION_URL}/upload`, form);
  }

  /** Deletes the uploaded profile on the server. */
  discardUpload(): Observable<void> {
    return this.http.delete<void>(`${EMBER_MIGRATION_URL}/upload`);
  }

  /** Starts the import or dry run in the background and returns the started job. */
  startImport(request: EmberImportRequest): Observable<Job> {
    return this.http.post<Job>(`${EMBER_MIGRATION_URL}/import`, request);
  }
}
