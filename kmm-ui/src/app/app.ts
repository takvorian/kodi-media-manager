import { Component, computed, inject } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { filter, map } from 'rxjs';

import { chapterForUrl } from './help/help';
import { LanguageSwitch } from './i18n/language-switch';
import { APP_NAME } from './i18n/translated-title-strategy';
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
    LanguageSwitch,
    TranslocoPipe,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly systemInfoService = inject(SystemInfoService);

  protected readonly title = APP_NAME;
  protected readonly systemInfo = rxResource({ stream: () => this.systemInfoService.load() });
  protected readonly navigation = [
    { path: '/movies', label: 'nav.movies' },
    { path: '/tvshows', label: 'nav.tvshows' },
    { path: '/moviesets', label: 'nav.moviesets' },
    { path: '/tags', label: 'nav.tags' },
    { path: '/sources', label: 'nav.sources' },
    { path: '/kodi', label: 'nav.kodi' },
    { path: '/settings', label: 'nav.settings' },
    { path: '/migration', label: 'nav.migration' },
    { path: '/backup', label: 'nav.backup' },
    { path: '/system', label: 'nav.system' },
    { path: '/help', label: 'nav.help' },
  ];

  private readonly url = toSignal(
    inject(Router).events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: '' },
  );

  /** Opens in a new tab so unsaved input on the current page is kept. */
  protected readonly pageHelp = computed(() => `/help/${chapterForUrl(this.url())}`);
}
