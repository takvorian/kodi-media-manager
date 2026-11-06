import { LocalizedText } from '../i18n/localized-text';

const DATABASE_FILE = /^MyVideos(\d+)\.emm$/i;
const SETTINGS_FILES = new Set(['Settings.xml', 'AdvancedSettings.xml', 'Interface.Kodi.xml']);
const DEFAULT_PROFILE = /(^|\/)Profiles\/Default$/;

/** A folder with an Ember database from the chosen directory, with its newest database. */
interface ProfileFolder {
  readonly path: string;
  readonly database: File;
  readonly schemaVersion: number;
}

/**
 * Picks the files the import needs from an Ember folder; everything else stays on this computer. The folder may be
 * the installation or the profile. Among several profiles {@code Profiles/Default} wins.
 * Returns an empty list if there is no Ember database.
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

/** Job key of the import; must match EmberImportService.JOB_KEY. */
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

/** Kodi device from Ember, without credentials. */
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

/** Response of POST /api/v1/migrations/ember/upload. */
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

/** Body of POST /api/v1/migrations/ember/import. */
export interface EmberImportRequest {
  readonly pathMappings: readonly PathMapping[];
  readonly importSettings: boolean;
  readonly importKodiHosts: boolean;
  readonly importTvShows: boolean;
  readonly dryRun: boolean;
}

/** Result of the import for TV shows and episodes. */
export interface EmberTvShowsReport {
  readonly showsCreated: number;
  readonly showsUpdated: number;
  readonly showsSkipped: number;
  readonly episodesCreated: number;
  readonly episodesUpdated: number;
  readonly episodesSkipped: number;
  /** Episodes Ember knows without a file; they are not imported. */
  readonly episodesMissing: number;
}

/** Result of a successful import job. */
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
  readonly settingsChanges: readonly LocalizedText[];
  readonly skippedSources: readonly LocalizedText[];
  /** Names of the movies, not translated. */
  readonly missingFiles: readonly string[];
  readonly warnings: readonly LocalizedText[];
}
