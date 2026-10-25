import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { CreateMediaSource, MediaSource, ScanResult } from './media-source';

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

  scan(sourceId: number): Observable<ScanResult> {
    return this.http.post<ScanResult>(`${SOURCES_URL}/${sourceId}/scan`, null);
  }
}
