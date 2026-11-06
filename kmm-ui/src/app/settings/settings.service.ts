import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiKeyName, ApiKeyView, KmmSettings, SettingsView } from './settings';

export const SETTINGS_URL = '/api/v1/settings';

@Injectable({ providedIn: 'root' })
export class SettingsService {
  private readonly http = inject(HttpClient);

  load(): Observable<SettingsView> {
    return this.http.get<SettingsView>(SETTINGS_URL);
  }

  save(settings: KmmSettings): Observable<SettingsView> {
    return this.http.put<SettingsView>(SETTINGS_URL, settings);
  }

  apiKeys(): Observable<ApiKeyView[]> {
    return this.http.get<ApiKeyView[]>(`${SETTINGS_URL}/api-keys`);
  }

  /** A value replaces the key, an empty value deletes it, missing keys stay unchanged. */
  saveApiKeys(changes: Partial<Record<ApiKeyName, string>>): Observable<ApiKeyView[]> {
    return this.http.put<ApiKeyView[]>(`${SETTINGS_URL}/api-keys`, changes);
  }
}
