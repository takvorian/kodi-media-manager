import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Job } from '../jobs/job';
import { EmberImportRequest, EmberInspection } from './ember-migration';

export const EMBER_MIGRATION_URL = '/api/v1/migrations/ember';

@Injectable({ providedIn: 'root' })
export class EmberMigrationService {
  private readonly http = inject(HttpClient);

  /** Lädt die Profildateien hoch und liefert die Analyse des Profils. */
  upload(files: readonly File[]): Observable<EmberInspection> {
    const form = new FormData();
    files.forEach((file) => form.append('files', file, file.name));
    return this.http.post<EmberInspection>(`${EMBER_MIGRATION_URL}/upload`, form);
  }

  /** Löscht das hochgeladene Profil auf dem Server. */
  discardUpload(): Observable<void> {
    return this.http.delete<void>(`${EMBER_MIGRATION_URL}/upload`);
  }

  /** Startet Import oder Probelauf im Hintergrund und liefert den gestarteten Job. */
  startImport(request: EmberImportRequest): Observable<Job> {
    return this.http.post<Job>(`${EMBER_MIGRATION_URL}/import`, request);
  }
}
