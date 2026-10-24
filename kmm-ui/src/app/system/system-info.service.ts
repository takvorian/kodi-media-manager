import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { SystemInfo } from './system-info';

export const SYSTEM_INFO_URL = '/api/v1/system/info';

@Injectable({ providedIn: 'root' })
export class SystemInfoService {
  private readonly http = inject(HttpClient);

  load(): Observable<SystemInfo> {
    return this.http.get<SystemInfo>(SYSTEM_INFO_URL);
  }
}
