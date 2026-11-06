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

  /** @param language the language, or {@code null} to use the settings */
  changeLanguage(sourceId: number, language: string | null): Observable<MediaSource> {
    return this.http.put<MediaSource>(`${SOURCES_URL}/${sourceId}/language`, { language });
  }

  delete(sourceId: number): Observable<void> {
    return this.http.delete<void>(`${SOURCES_URL}/${sourceId}`);
  }

  /** Starts the scan in the background and returns the started job. */
  scan(sourceId: number): Observable<Job> {
    return this.http.post<Job>(`${SOURCES_URL}/${sourceId}/scan`, null);
  }

  /** Starts auto-scraping of missing data in the background and returns the started job. */
  autoScrape(sourceId: number): Observable<Job> {
    return this.http.post<Job>(`${SOURCES_URL}/${sourceId}/auto-scrape`, null);
  }
}
