import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  ScraperPlugin,
  ScraperPluginDetails,
  ScraperPriority,
  ScraperPriorityChange,
} from './scraper-plugin';

export const SCRAPER_PLUGINS_URL = '/api/v1/scrapers/plugins';
export const SCRAPER_PRIORITIES_URL = '/api/v1/scrapers/priorities';

export function sourcePrioritiesUrl(sourceId: number): string {
  return `/api/v1/sources/${sourceId}/scraper-priorities`;
}

/** The scraper plugins with their settings, for the settings pages. */
@Injectable({ providedIn: 'root' })
export class ScraperAdminService {
  private readonly http = inject(HttpClient);

  plugins(): Observable<ScraperPlugin[]> {
    return this.http.get<ScraperPlugin[]>(SCRAPER_PLUGINS_URL);
  }

  details(pluginId: string): Observable<ScraperPluginDetails> {
    return this.http.get<ScraperPluginDetails>(this.url(pluginId));
  }

  /** A value replaces a setting, an empty value deletes it, missing settings stay unchanged. */
  changeSettings(
    pluginId: string,
    changes: Readonly<Record<string, string>>,
  ): Observable<ScraperPluginDetails> {
    return this.http.put<ScraperPluginDetails>(`${this.url(pluginId)}/settings`, changes);
  }

  setEnabled(pluginId: string, enabled: boolean): Observable<ScraperPluginDetails> {
    return this.http.post<ScraperPluginDetails>(
      `${this.url(pluginId)}/${enabled ? 'enable' : 'disable'}`,
      null,
    );
  }

  /** The orders from the settings for movies and TV shows. */
  priorities(): Observable<ScraperPriority[]> {
    return this.http.get<ScraperPriority[]>(SCRAPER_PRIORITIES_URL);
  }

  changePriorities(changes: readonly ScraperPriorityChange[]): Observable<ScraperPriority[]> {
    return this.http.put<ScraperPriority[]>(SCRAPER_PRIORITIES_URL, changes);
  }

  /** The orders of a source; kinds without an own one show those from the settings. */
  sourcePriorities(sourceId: number): Observable<ScraperPriority[]> {
    return this.http.get<ScraperPriority[]>(sourcePrioritiesUrl(sourceId));
  }

  changeSourcePriorities(
    sourceId: number,
    changes: readonly ScraperPriorityChange[],
  ): Observable<ScraperPriority[]> {
    return this.http.put<ScraperPriority[]>(sourcePrioritiesUrl(sourceId), changes);
  }

  private url(pluginId: string): string {
    return `${SCRAPER_PLUGINS_URL}/${encodeURIComponent(pluginId)}`;
  }
}
