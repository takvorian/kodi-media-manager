import { MatSnackBarConfig } from '@angular/material/snack-bar';
import { translate } from '@jsverse/transloco';

import { Job } from '../jobs/job';
import { finishedJobSummary, unsuccessfulJobSummary } from '../sources/job-summary';
import { LocalizedText, literal, localize } from '../i18n/localized-text';

const SYNC_MESSAGE_DURATION_MS = 5000;
const PLAYBACK = 'kodi.playback';

/** Maps a directory as KMM sees it to the same directory as Kodi sees it. */
export interface KodiPathMapping {
  readonly localPath: string;
  readonly kodiPath: string;
}

/** A Kodi device as the backend returns it (without password). */
export interface KodiHost {
  readonly id: number;
  readonly name: string;
  readonly address: string;
  readonly port: number;
  readonly username: string | null;
  readonly passwordSet: boolean;
  readonly realtimeSync: boolean;
  readonly pathMappings: readonly KodiPathMapping[];
  /** Last sync since KMM started. */
  readonly lastSync: KodiSyncResult | null;
}

/** Create or change; a missing password (`null`) keeps the stored one. */
export interface KodiHostCommand {
  readonly name: string;
  readonly address: string;
  readonly port: number;
  readonly username: string | null;
  readonly password: string | null;
  readonly realtimeSync: boolean;
  readonly pathMappings: readonly KodiPathMapping[];
}

export interface KodiConnectionTest {
  readonly version: string;
  /** Hint on Kodi paths of the mapping for which Kodi has no source. */
  readonly warning: LocalizedText | null;
}

export type KodiSyncOutcome =
  | 'REFRESHED'
  | 'SCAN_STARTED'
  | 'UPDATED'
  | 'REMOVED'
  | 'NOT_IN_KODI'
  | 'NOT_MAPPED'
  | 'NO_KODI_SOURCE'
  | 'FAILED';

/** Result of a sync with a Kodi device. */
export interface KodiSyncResult {
  readonly hostId: number;
  readonly host: string;
  readonly outcome: KodiSyncOutcome;
  readonly message: LocalizedText;
  readonly at: string;
}

export function syncFailed(result: KodiSyncResult): boolean {
  return (
    result.outcome === 'FAILED' ||
    result.outcome === 'NOT_MAPPED' ||
    result.outcome === 'NO_KODI_SOURCE'
  );
}

/** Problems stay on screen until they are closed, so they can be read and copied. */
export function kodiSyncSnackBarConfig(results: readonly KodiSyncResult[]): MatSnackBarConfig {
  return results.some(syncFailed) ? {} : { duration: SYNC_MESSAGE_DURATION_MS };
}

/** Sums up the results of all devices for a snack bar message in the active language. */
export function kodiSyncSummary(results: readonly KodiSyncResult[]): string {
  if (results.length === 0) {
    return translate('kodi.syncNoHost');
  }
  return results.map((result) => `${result.host}: ${localize(result.message)}`).join(' ');
}

/** Prefix of the job key for refreshing a TV show or season; must match KodiBatchRefreshService.TV_SHOW_JOB_KEY_PREFIX. */
export const KODI_TV_SHOW_JOB_KEY_PREFIX = 'kodi-tvshow:';

export function kodiTvShowJobKey(showId: number): string {
  return `${KODI_TV_SHOW_JOB_KEY_PREFIX}${showId}`;
}

/** The results of all devices a finished refresh job of a TV show or season delivers. */
export function kodiJobResults(job: Job): KodiSyncResult[] {
  return Array.isArray(job.result) ? (job.result as KodiSyncResult[]) : [];
}

/** Prefix of the job key for fetching the watched status; must match KodiPlaybackService.JOB_KEY_PREFIX. */
export const KODI_PLAYBACK_JOB_KEY_PREFIX = 'kodi-playback:';

export function kodiPlaybackJobKey(sourceId: number): string {
  return `${KODI_PLAYBACK_JOB_KEY_PREFIX}${sourceId}`;
}

export type KodiPlaybackOutcome = 'UPDATED' | 'UNCHANGED' | 'KEPT' | 'NOT_IN_KODI' | 'FAILED';

/** Result of taking over the watched status and user rating of a movie or an episode from Kodi. */
export interface KodiPlaybackResult {
  readonly host: string;
  readonly outcome: KodiPlaybackOutcome;
  readonly message: LocalizedText;
  /** State in KMM afterwards. */
  readonly playCount: number | null;
  readonly lastPlayedAt: string | null;
  readonly userRating: number | null;
}

/** Result of taking over the watched status of a whole source; the failure list is truncated by the server. */
export interface KodiPlaybackImportResult {
  readonly sourceId: number;
  readonly host: string;
  readonly updated: number;
  readonly unchanged: number;
  /** KMM knows more than Kodi and keeps its state. */
  readonly kept: number;
  readonly notInKodi: number;
  readonly failed: number;
  readonly failures: readonly LocalizedText[];
}

/** Message after taking over the watched status of a movie or an episode, in the active language. */
export function kodiPlaybackMessage(result: KodiPlaybackResult): string {
  return `${result.host}: ${localize(result.message)}`;
}

/** Sums up a finished job "fetch watched status" for a snack bar message in the active language. */
export function kodiPlaybackSummary(job: Job): string {
  if (job.status !== 'SUCCEEDED') {
    return unsuccessfulJobSummary(job);
  }
  const result = job.result as KodiPlaybackImportResult;
  return finishedJobSummary({ ...job, title: literal(`${localize(job.title)} (${result.host})`) }, [
    translate(`${PLAYBACK}.updated`, { count: result.updated }),
    translate(`${PLAYBACK}.unchanged`, { count: result.unchanged }),
    translate(`${PLAYBACK}.notInKodi`, { count: result.notInKodi }),
    ...(result.kept > 0 ? [translate(`${PLAYBACK}.kept`, { count: result.kept })] : []),
    ...(result.failed > 0 ? [translate(`${PLAYBACK}.failed`, { count: result.failed })] : []),
  ]);
}
