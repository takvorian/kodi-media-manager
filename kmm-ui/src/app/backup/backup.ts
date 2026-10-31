/** Schlüssel des Import-Jobs, wie ihn das Backend vergibt. */
export const BACKUP_IMPORT_JOB_KEY = 'backup-import';

export type BackupImportMode = 'REPLACE' | 'MERGE';

export interface BackupSource {
  readonly name: string;
  readonly path: string;
  readonly mediaType: string;
}

/** Was eine hochgeladene Sicherung enthält. */
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
  readonly warnings: readonly string[];
}

const TABLE_LABELS: Readonly<Record<string, string>> = {
  media_source: 'Quellen',
  movie_set: 'Filmsets',
  movie: 'Filme',
  tv_show: 'Serien',
  episode: 'Episoden',
  kodi_host: 'Kodi-Geräte',
  saved_filter: 'Filter',
  app_setting: 'Einstellungen',
};

/** Die Tabellen, die der Benutzer als Einträge kennt; abhängige Listen zählen zu ihnen. */
export const VISIBLE_TABLES = Object.keys(TABLE_LABELS);

export function tableLabel(table: string): string {
  return TABLE_LABELS[table] ?? table;
}
