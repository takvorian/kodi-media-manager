import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'movies' },
  {
    path: 'movies',
    title: 'Filme – KMM',
    loadComponent: () => import('./movies/movie-list').then((m) => m.MovieList),
  },
  {
    path: 'movies/:id',
    title: 'Film – KMM',
    loadComponent: () => import('./movies/movie-detail').then((m) => m.MovieDetail),
  },
  {
    path: 'tvshows',
    title: 'Serien – KMM',
    loadComponent: () => import('./tvshows/tvshow-list').then((m) => m.TvShowList),
  },
  {
    path: 'tvshows/:id',
    title: 'Serie – KMM',
    loadComponent: () => import('./tvshows/tvshow-detail').then((m) => m.TvShowDetail),
  },
  {
    path: 'episodes/:id',
    title: 'Episode – KMM',
    loadComponent: () => import('./tvshows/episode-detail').then((m) => m.EpisodeDetail),
  },
  {
    path: 'sources',
    title: 'Quellen – KMM',
    loadComponent: () => import('./sources/source-list').then((m) => m.SourceList),
  },
  {
    path: 'sources/:id/rename',
    title: 'Filme umbenennen – KMM',
    loadComponent: () => import('./movies/movie-rename-page').then((m) => m.MovieRenamePage),
  },
  {
    path: 'sources/:id/tv-rename',
    title: 'Serien umbenennen – KMM',
    loadComponent: () => import('./tvshows/tv-rename-page').then((m) => m.TvRenamePage),
  },
  {
    path: 'kodi',
    title: 'Kodi – KMM',
    loadComponent: () => import('./kodi/kodi-hosts-page').then((m) => m.KodiHostsPage),
  },
  {
    path: 'settings',
    title: 'Einstellungen – KMM',
    loadComponent: () => import('./settings/settings-page').then((m) => m.SettingsPage),
  },
  {
    path: 'migration',
    title: 'Ember-Import – KMM',
    loadComponent: () => import('./migration/ember-import-page').then((m) => m.EmberImportPage),
  },
  {
    path: 'system',
    title: 'System – KMM',
    loadComponent: () => import('./system/system-status').then((m) => m.SystemStatus),
  },
  { path: '**', redirectTo: 'movies' },
];
