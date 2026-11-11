import { Component, computed, inject } from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { filter, map } from 'rxjs';

import { LOGIN_PATH } from './auth/auth.guard';
import { AuthService } from './auth/auth.service';
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
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

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
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: '' },
  );

  /** Opens in a new tab so unsaved input on the current page is kept. */
  protected readonly pageHelp = computed(() => `/help/${chapterForUrl(this.url())}`);

  /** Navigation and jobs only for a signed-in user; the login page shows nothing of the library. */
  protected readonly signedIn = computed(
    () => this.auth.signedIn() && !this.url().startsWith(LOGIN_PATH),
  );
  protected readonly username = this.auth.username;

  constructor() {
    // A new page starts at the top; the lists restore their own position when coming back. The browser must not
    // scroll on back and forward itself, otherwise the list notes the position of the page it comes back from.
    history.scrollRestoration = 'manual';
    let path: string | null = null;
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        map((event) => event.urlAfterRedirects.split(/[?#]/)[0]),
        takeUntilDestroyed(),
      )
      .subscribe((next) => {
        if (path !== null && next !== path) {
          window.scrollTo(0, 0);
        }
        path = next;
      });
  }

  protected logout(): void {
    this.auth.logout().subscribe({
      complete: () => void this.router.navigateByUrl(LOGIN_PATH),
      error: () => void this.router.navigateByUrl(LOGIN_PATH),
    });
  }
}
