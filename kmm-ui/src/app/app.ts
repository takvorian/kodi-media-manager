import { Component, computed, inject } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { chapterForUrl } from './help/help';
import { JobIndicator } from './jobs/job-indicator';
import { SystemInfoService } from './system/system-info.service';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatButtonModule,
    JobIndicator,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly systemInfoService = inject(SystemInfoService);

  protected readonly title = 'Kodi Media Manager';
  protected readonly systemInfo = rxResource({ stream: () => this.systemInfoService.load() });
  protected readonly navigation = [
    { path: '/movies', label: 'Filme' },
    { path: '/tvshows', label: 'Serien' },
    { path: '/moviesets', label: 'Filmsets' },
    { path: '/tags', label: 'Tags' },
    { path: '/sources', label: 'Quellen' },
    { path: '/kodi', label: 'Kodi' },
    { path: '/settings', label: 'Einstellungen' },
    { path: '/migration', label: 'Ember-Import' },
    { path: '/system', label: 'System' },
    { path: '/help', label: 'Hilfe' },
  ];

  private readonly url = toSignal(
    inject(Router).events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: '' },
  );

  /** Öffnet in einem neuen Tab, damit nicht gespeicherte Eingaben der aktuellen Seite erhalten bleiben. */
  protected readonly pageHelp = computed(() => `/help/${chapterForUrl(this.url())}`);
}
