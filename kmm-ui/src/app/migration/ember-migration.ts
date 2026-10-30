const DATABASE_FILE = /^MyVideos(\d+)\.emm$/i;
const SETTINGS_FILES = new Set(['Settings.xml', 'AdvancedSettings.xml', 'Interface.Kodi.xml']);
const DEFAULT_PROFILE = /(^|\/)Profiles\/Default$/;

/** Ein Ordner mit Ember-Datenbank aus dem gewählten Verzeichnis, samt der neuesten Datenbank. */
interface ProfileFolder {
  readonly path: string;
  readonly database: File;
  readonly schemaVersion: number;
}

/**
 * Wählt aus einem Ember-Ordner die Dateien aus, die der Import braucht; alles andere bleibt auf diesem Rechner. Der
 * Ordner darf die Installation oder das Profil sein. Unter mehreren Profilen gewinnt {@code Profiles/Default}.
 * Liefert eine leere Liste, wenn keine Ember-Datenbank dabei ist.
 */
export function emberProfileFiles(files: readonly File[]): File[] {
  const folders = new Map<string, ProfileFolder>();
  for (const file of files) {
    const version = DATABASE_FILE.exec(file.name)?.[1];
    const path = parentFolder(file);
    if (version && Number(version) > (folders.get(path)?.schemaVersion ?? -1)) {
      folders.set(path, { path, database: file, schemaVersion: Number(version) });
    }
  }
  const candidates = [...folders.values()];
  const chosen =
    candidates.find((folder) => DEFAULT_PROFILE.test(folder.path)) ??
    candidates.sort((a, b) => b.schemaVersion - a.schemaVersion)[0];
  if (!chosen) {
    return [];
  }
  return [
    chosen.database,
    ...files.filter((file) => parentFolder(file) === chosen.path && SETTINGS_FILES.has(file.name)),
  ];
}

function parentFolder(file: File): string {
  return file.webkitRelativePath.split('/').slice(0, -1).join('/');
}

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

/** Antwort von POST /api/v1/migrations/ember/upload. */
export interface EmberInspection {
  readonly profileDirectory: string;
  readonly schemaVersion: number;
  readonly library: EmberLibraryStatistics;
  readonly sources: readonly EmberSource[];
  readonly tvSources: readonly EmberSource[];
  readonly kodiHosts: readonly EmberKodiHost[];
  readonly settings: EmberSettingsSummary | null;
}

export interface PathMapping {
  readonly emberPath: string;
  readonly kmmPath: string;
}

/** Inhalt von POST /api/v1/migrations/ember/import. */
export interface EmberImportRequest {
  readonly pathMappings: readonly PathMapping[];
  readonly importSettings: boolean;
  readonly importKodiHosts: boolean;
  readonly importTvShows: boolean;
  readonly dryRun: boolean;
}

/** Ergebnis des Imports für Serien und Episoden. */
export interface EmberTvShowsReport {
  readonly showsCreated: number;
  readonly showsUpdated: number;
  readonly showsSkipped: number;
  readonly episodesCreated: number;
  readonly episodesUpdated: number;
  readonly episodesSkipped: number;
  /** Episoden, die Ember ohne Datei kennt; sie werden nicht übernommen. */
  readonly episodesMissing: number;
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
  readonly tvShows: EmberTvShowsReport;
  readonly kodiHostsCreated: number;
  readonly kodiHostsUpdated: number;
  readonly settingsChanges: readonly string[];
  readonly skippedSources: readonly string[];
  readonly missingFiles: readonly string[];
  readonly warnings: readonly string[];
}
