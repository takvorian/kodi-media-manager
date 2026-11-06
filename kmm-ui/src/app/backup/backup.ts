import { translate } from '@jsverse/transloco';

import { LocalizedText } from '../i18n/localized-text';

/** Key of the import job as the backend assigns it. */
export const BACKUP_IMPORT_JOB_KEY = 'backup-import';

export type BackupImportMode = 'REPLACE' | 'MERGE';

export interface BackupSource {
  readonly name: string;
  readonly path: string;
  readonly mediaType: string;
}

/** What an uploaded backup contains. */
export interface BackupHeader {
  readonly schemaVersion: string;
  readonly applicationVersion: string | null;
  readonly createdAt: string | null;
  readonly credentials: boolean;
  readonly counts: Readonly<Record<string, number>>;
  readonly sources: readonly BackupSource[];
}

export interface BackupPathMapping {
  readonly from: string;
  readonly to: string;
}

export interface BackupImportRequest {
  readonly mode: BackupImportMode;
  readonly pathMappings: readonly BackupPathMapping[];
  readonly dryRun: boolean;
}

export interface BackupTableResult {
  readonly table: string;
  readonly created: number;
  readonly updated: number;
  readonly skipped: number;
}

export interface BackupImportReport {
  readonly dryRun: boolean;
  readonly mode: BackupImportMode;
  readonly results: readonly BackupTableResult[];
  readonly unmappedPaths: number;
  readonly credentialsKept: boolean;
  readonly safetyCopy: string | null;
  readonly warnings: readonly LocalizedText[];
}

/** Translation keys of the table labels. */
const TABLE_LABELS: Readonly<Record<string, string>> = {
  media_source: 'backup.table.mediaSource',
  movie_set: 'backup.table.movieSet',
  movie: 'backup.table.movie',
  tv_show: 'backup.table.tvShow',
  episode: 'backup.table.episode',
  kodi_host: 'backup.table.kodiHost',
  saved_filter: 'backup.table.savedFilter',
  app_setting: 'backup.table.appSetting',
};

/** The tables the user knows as entries; dependent lists count towards them. */
export const VISIBLE_TABLES = Object.keys(TABLE_LABELS);

/** Label of a table in the active language; an unknown table keeps its name. */
export function tableLabel(table: string): string {
  const key = TABLE_LABELS[table];
  return key ? translate(key) : table;
}
