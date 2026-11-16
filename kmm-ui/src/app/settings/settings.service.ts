import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { KmmSettings, SettingsView } from './settings';

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
}
