import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Job } from '../jobs/job';
import { CreateMediaSource, MediaSource } from './media-source';

export const SOURCES_URL = '/api/v1/sources';

@Injectable({ providedIn: 'root' })
export class MediaSourceService {
  private readonly http = inject(HttpClient);

  findAll(): Observable<MediaSource[]> {
    return this.http.get<MediaSource[]>(SOURCES_URL);
  }

  create(source: CreateMediaSource): Observable<MediaSource> {
    return this.http.post<MediaSource>(SOURCES_URL, source);
  }

  delete(sourceId: number): Observable<void> {
    return this.http.delete<void>(`${SOURCES_URL}/${sourceId}`);
  }

  /** Startet den Scan im Hintergrund und liefert den gestarteten Job. */
  scan(sourceId: number): Observable<Job> {
    return this.http.post<Job>(`${SOURCES_URL}/${sourceId}/scan`, null);
  }

  /** Startet das automatische Scrapen fehlender Daten im Hintergrund und liefert den gestarteten Job. */
  autoScrape(sourceId: number): Observable<Job> {
    return this.http.post<Job>(`${SOURCES_URL}/${sourceId}/auto-scrape`, null);
  }
}
