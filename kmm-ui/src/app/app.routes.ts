import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'movies' },
  { path: 'movies', title: 'Filme – KMM', loadComponent: () => import('./movies/movie-list').then((m) => m.MovieList) },
  {
    path: 'movies/:id',
    title: 'Film – KMM',
    loadComponent: () => import('./movies/movie-detail').then((m) => m.MovieDetail),
  },
  {
    path: 'sources',
    title: 'Quellen – KMM',
    loadComponent: () => import('./sources/source-list').then((m) => m.SourceList),
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
