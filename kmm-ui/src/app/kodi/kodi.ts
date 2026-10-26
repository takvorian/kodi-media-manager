import { Job } from '../jobs/job';

/** Zuordnung eines Verzeichnisses aus Sicht von KMM zu demselben Verzeichnis aus Sicht von Kodi. */
export interface KodiPathMapping {
  readonly localPath: string;
  readonly kodiPath: string;
}

/** Ein Kodi-Gerät, wie das Backend es liefert (ohne Passwort). */
export interface KodiHost {
  readonly id: number;
  readonly name: string;
  readonly address: string;
  readonly port: number;
  readonly username: string | null;
  readonly passwordSet: boolean;
  readonly realtimeSync: boolean;
  readonly pathMappings: readonly KodiPathMapping[];
  /** Letzter Abgleich seit dem Start von KMM. */
  readonly lastSync: KodiSyncResult | null;
}

/** Anlegen oder Ändern; ein fehlendes Passwort (`null`) behält das gespeicherte. */
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
}

export type KodiSyncOutcome = 'REFRESHED' | 'SCAN_STARTED' | 'NOT_MAPPED' | 'FAILED';

/** Ergebnis eines Abgleichs mit einem Kodi-Gerät. */
export interface KodiSyncResult {
  readonly hostId: number;
  readonly host: string;
  readonly outcome: KodiSyncOutcome;
  readonly message: string;
  readonly at: string;
}

export function syncFailed(result: KodiSyncResult): boolean {
  return result.outcome === 'FAILED' || result.outcome === 'NOT_MAPPED';
}

/** Fasst die Ergebnisse aller Geräte für eine Snackbar-Meldung zusammen. */
export function kodiSyncSummary(results: readonly KodiSyncResult[]): string {
  if (results.length === 0) {
    return 'Kein Kodi-Gerät eingerichtet – unter „Kodi“ anlegen.';
  }
  return results.map((result) => `${result.host}: ${result.message}`).join(' ');
}

/** Präfix des Job-Schlüssels beim Holen des Gesehen-Status; muss zu KodiPlaybackService.JOB_KEY_PREFIX passen. */
export const KODI_PLAYBACK_JOB_KEY_PREFIX = 'kodi-playback:';

export function kodiPlaybackJobKey(sourceId: number): string {
  return `${KODI_PLAYBACK_JOB_KEY_PREFIX}${sourceId}`;
}

export type KodiPlaybackOutcome = 'UPDATED' | 'UNCHANGED' | 'KEPT' | 'NOT_IN_KODI' | 'FAILED';

/** Ergebnis der Übernahme des Gesehen-Status eines Films oder einer Episode aus Kodi. */
export interface KodiPlaybackResult {
  readonly host: string;
  readonly outcome: KodiPlaybackOutcome;
  readonly message: string;
  /** Stand in KMM danach. */
  readonly playCount: number | null;
  readonly lastPlayedAt: string | null;
}

/** Ergebnis der Übernahme des Gesehen-Status einer ganzen Quelle; die Fehlerliste ist serverseitig gekürzt. */
export interface KodiPlaybackImportResult {
  readonly sourceId: number;
  readonly host: string;
  readonly updated: number;
  readonly unchanged: number;
  /** KMM weiß mehr als Kodi und behält seinen Stand. */
  readonly kept: number;
  readonly notInKodi: number;
  readonly failed: number;
  readonly failures: readonly string[];
}

/** Fasst einen beendeten Job „Gesehen-Status holen“ für eine Snackbar-Meldung zusammen. */
export function kodiPlaybackSummary(job: Job): string {
  if (job.status === 'SUCCEEDED') {
    const result = job.result as KodiPlaybackImportResult;
    const kept = result.kept > 0 ? `, ${result.kept} abweichend (KMM-Stand behalten)` : '';
    const failed = result.failed > 0 ? `, ${result.failed} Fehler` : '';
    return (
      `${job.title} (${result.host}) abgeschlossen: ${result.updated} übernommen, ` +
      `${result.unchanged} unverändert, ${result.notInKodi} in Kodi unbekannt${kept}${failed}.`
    );
  }
  return job.status === 'CANCELLED'
    ? `${job.title} abgebrochen.`
    : `${job.title} fehlgeschlagen: ${job.message ?? 'Unbekannter Fehler'}`;
}
