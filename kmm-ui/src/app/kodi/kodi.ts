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
