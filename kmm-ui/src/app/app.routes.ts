import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'movies' },
  { path: 'movies', title: 'Filme – KMM', loadComponent: () => import('./movies/movie-list').then((m) => m.MovieList) },
  {
    path: 'sources',
    title: 'Quellen – KMM',
    loadComponent: () => import('./sources/source-list').then((m) => m.SourceList),
  },
  {
    path: 'system',
    title: 'System – KMM',
    loadComponent: () => import('./system/system-status').then((m) => m.SystemStatus),
  },
  { path: '**', redirectTo: 'movies' },
];
