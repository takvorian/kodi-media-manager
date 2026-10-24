import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterOutlet } from '@angular/router';
import { Observable, catchError, map, of, startWith } from 'rxjs';

import { SystemInfo } from './system/system-info';
import { SystemInfoService } from './system/system-info.service';

export type SystemInfoState =
  | { readonly status: 'loading' }
  | { readonly status: 'loaded'; readonly info: SystemInfo }
  | { readonly status: 'failed' };

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, MatToolbarModule, MatCardModule],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly title = 'Kodi Media Manager';
  protected readonly systemInfo = toSignal(this.loadSystemInfo(inject(SystemInfoService)), {
    requireSync: true,
  });

  private loadSystemInfo(service: SystemInfoService): Observable<SystemInfoState> {
    return service.load().pipe(
      map((info): SystemInfoState => ({ status: 'loaded', info })),
      catchError(() => of<SystemInfoState>({ status: 'failed' })),
      startWith<SystemInfoState>({ status: 'loading' }),
    );
  }
}
