import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { JobIndicator } from './jobs/job-indicator';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatToolbarModule, MatButtonModule, JobIndicator],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly title = 'Kodi Media Manager';
  protected readonly navigation = [
    { path: '/movies', label: 'Filme' },
    { path: '/tvshows', label: 'Serien' },
    { path: '/sources', label: 'Quellen' },
    { path: '/kodi', label: 'Kodi' },
    { path: '/settings', label: 'Einstellungen' },
    { path: '/migration', label: 'Ember-Import' },
    { path: '/system', label: 'System' },
  ];
}
