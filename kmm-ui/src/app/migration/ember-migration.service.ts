import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Job } from '../jobs/job';
import { EmberImportRequest, EmberInspection } from './ember-migration';

export const EMBER_MIGRATION_URL = '/api/v1/migrations/ember';

@Injectable({ providedIn: 'root' })
export class EmberMigrationService {
  private readonly http = inject(HttpClient);

  inspect(path: string): Observable<EmberInspection> {
    return this.http.get<EmberInspection>(`${EMBER_MIGRATION_URL}/inspect`, { params: { path } });
  }

  /** Startet Import oder Probelauf im Hintergrund und liefert den gestarteten Job. */
  startImport(request: EmberImportRequest): Observable<Job> {
    return this.http.post<Job>(`${EMBER_MIGRATION_URL}/import`, request);
  }
}
