import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ChangelogVersion, SystemInfo } from './system-info';

export const SYSTEM_INFO_URL = '/api/v1/system/info';
export const CHANGELOG_URL = '/api/v1/system/changelog';

@Injectable({ providedIn: 'root' })
export class SystemInfoService {
  private readonly http = inject(HttpClient);

  load(): Observable<SystemInfo> {
    return this.http.get<SystemInfo>(SYSTEM_INFO_URL);
  }

  changelog(): Observable<ChangelogVersion[]> {
    return this.http.get<ChangelogVersion[]>(CHANGELOG_URL);
  }
}
