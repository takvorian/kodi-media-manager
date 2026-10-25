/** Job-Schlüssel des Imports; muss zu EmberImportService.JOB_KEY passen. */
export const EMBER_IMPORT_JOB_KEY = 'ember-import';

export interface EmberLibraryStatistics {
  readonly movies: number;
  readonly movieSets: number;
  readonly tvShows: number;
  readonly episodes: number;
  readonly movieSources: number;
  readonly tvShowSources: number;
}

export interface EmberSource {
  readonly name: string | null;
  readonly path: string | null;
}

/** Kodi-Gerät aus Ember, ohne Zugangsdaten. */
export interface EmberKodiHost {
  readonly name: string | null;
  readonly address: string;
  readonly port: number;
  readonly pathMappings: number;
}

export interface EmberSettingsSummary {
  readonly movieLanguage: string | null;
  readonly movieImageLanguage: string | null;
  readonly videoExtensions: readonly string[];
}

/** Antwort von GET /api/v1/migrations/ember/inspect. */
export interface EmberInspection {
  readonly profileDirectory: string;
  readonly schemaVersion: number;
  readonly library: EmberLibraryStatistics;
  readonly sources: readonly EmberSource[];
  readonly kodiHosts: readonly EmberKodiHost[];
  readonly settings: EmberSettingsSummary | null;
}

export interface PathMapping {
  readonly emberPath: string;
  readonly kmmPath: string;
}

/** Inhalt von POST /api/v1/migrations/ember/import. */
export interface EmberImportRequest {
  readonly profilePath: string;
  readonly pathMappings: readonly PathMapping[];
  readonly importSettings: boolean;
  readonly importKodiHosts: boolean;
  readonly dryRun: boolean;
}

/** Ergebnis eines erfolgreichen Import-Jobs. */
export interface EmberImportReport {
  readonly dryRun: boolean;
  readonly sourcesCreated: number;
  readonly sourcesReused: number;
  readonly moviesCreated: number;
  readonly moviesUpdated: number;
  readonly moviesSkipped: number;
  readonly moviesWithoutNfo: number;
  readonly kodiHostsCreated: number;
  readonly kodiHostsUpdated: number;
  readonly settingsChanges: readonly string[];
  readonly skippedSources: readonly string[];
  readonly missingFiles: readonly string[];
  readonly warnings: readonly string[];
}
