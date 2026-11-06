import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { LocalizedText } from '../i18n/localized-text';

/** The `theme.mp3` of a movie or TV show. */
export interface ThemeInfo {
  readonly exists: boolean;
  readonly path: string;
  readonly sizeBytes: number | null;
  readonly modifiedAt: string | null;
}

/** A theme music suggestion; `previewUrl` can be played directly, `webUrl` is the provider's page. */
export interface ThemeOption {
  readonly name: string | null;
  readonly subject: string | null;
  readonly source: string;
  readonly previewUrl: string | null;
  readonly webUrl: string;
  readonly durationSeconds: number | null;
  readonly scraperId: string;
  readonly scraperName: string;
  /** The provider lists it under the same title; KMM applies only those automatically. */
  readonly sameTitle: boolean;
}

/** Suggestions, same title first, and notes on providers that are not set up or failed. */
export interface ThemeOptions {
  readonly candidates: readonly ThemeOption[];
  readonly warnings: readonly LocalizedText[];
}

/** Theme music endpoints of a movie or TV show, e.g. `/api/v1/tvshows/3/theme`. */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly http = inject(HttpClient);

  info(endpoint: string): Observable<ThemeInfo> {
    return this.http.get<ThemeInfo>(endpoint);
  }

  candidates(endpoint: string): Observable<ThemeOptions> {
    return this.http.get<ThemeOptions>(`${endpoint}/candidates`);
  }

  /** Downloads the music and puts it into the folder as `theme.mp3`. */
  apply(endpoint: string, option: ThemeOption): Observable<ThemeInfo> {
    return this.http.put<ThemeInfo>(endpoint, option);
  }

  remove(endpoint: string): Observable<ThemeInfo> {
    return this.http.delete<ThemeInfo>(endpoint);
  }

  /** Address of the file for playback; the modification time bypasses old versions in the browser cache. */
  fileUrl(endpoint: string, info: ThemeInfo): string {
    return `${endpoint}/file?v=${encodeURIComponent(info.modifiedAt ?? '')}`;
  }
}
