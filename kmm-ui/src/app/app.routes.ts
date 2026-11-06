import { Routes } from '@angular/router';

import { DEFAULT_HELP_CHAPTER } from './help/help';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'movies' },
  {
    path: 'movies',
    title: 'pageTitle.movies',
    loadComponent: () => import('./movies/movie-list').then((m) => m.MovieList),
  },
  {
    path: 'movies/:id',
    title: 'pageTitle.movie',
    loadComponent: () => import('./movies/movie-detail').then((m) => m.MovieDetail),
  },
  {
    path: 'tvshows',
    title: 'pageTitle.tvshows',
    loadComponent: () => import('./tvshows/tvshow-list').then((m) => m.TvShowList),
  },
  {
    path: 'tvshows/:id',
    title: 'pageTitle.tvshow',
    loadComponent: () => import('./tvshows/tvshow-detail').then((m) => m.TvShowDetail),
  },
  {
    path: 'episodes/:id',
    title: 'pageTitle.episode',
    loadComponent: () => import('./tvshows/episode-detail').then((m) => m.EpisodeDetail),
  },
  {
    path: 'moviesets',
    title: 'pageTitle.moviesets',
    loadComponent: () => import('./moviesets/movie-set-manager').then((m) => m.MovieSetManager),
  },
  {
    path: 'tags',
    title: 'pageTitle.tags',
    loadComponent: () => import('./tags/tag-manager').then((m) => m.TagManager),
  },
  {
    path: 'sources',
    title: 'pageTitle.sources',
    loadComponent: () => import('./sources/source-list').then((m) => m.SourceList),
  },
  {
    path: 'sources/:id/rename',
    title: 'pageTitle.movieRename',
    loadComponent: () => import('./movies/movie-rename-page').then((m) => m.MovieRenamePage),
  },
  {
    path: 'sources/:id/tv-rename',
    title: 'pageTitle.tvRename',
    loadComponent: () => import('./tvshows/tv-rename-page').then((m) => m.TvRenamePage),
  },
  {
    path: 'kodi',
    title: 'pageTitle.kodi',
    loadComponent: () => import('./kodi/kodi-hosts-page').then((m) => m.KodiHostsPage),
  },
  {
    path: 'settings',
    title: 'pageTitle.settings',
    loadComponent: () => import('./settings/settings-page').then((m) => m.SettingsPage),
  },
  {
    path: 'migration',
    title: 'pageTitle.migration',
    loadComponent: () => import('./migration/ember-import-page').then((m) => m.EmberImportPage),
  },
  {
    path: 'backup',
    title: 'pageTitle.backup',
    loadComponent: () => import('./backup/backup-page').then((m) => m.BackupPage),
  },
  {
    path: 'system',
    title: 'pageTitle.system',
    loadComponent: () => import('./system/system-status').then((m) => m.SystemStatus),
  },
  { path: 'help', pathMatch: 'full', redirectTo: `help/${DEFAULT_HELP_CHAPTER}` },
  {
    path: 'help/:chapter',
    title: 'pageTitle.help',
    loadComponent: () => import('./help/help-page').then((m) => m.HelpPage),
  },
  { path: '**', redirectTo: 'movies' },
];
