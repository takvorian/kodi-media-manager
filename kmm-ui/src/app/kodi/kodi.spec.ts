import { job } from '../jobs/fake-job-events';
import {
  KodiSyncResult,
  kodiPlaybackJobKey,
  kodiPlaybackSummary,
  kodiSyncSnackBarConfig,
  kodiSyncSummary,
  syncFailed,
} from './kodi';

describe('kodi', () => {
  const result = (outcome: KodiSyncResult['outcome'], host = 'Wohnzimmer'): KodiSyncResult => ({
    hostId: 1,
    host,
    outcome,
    message: 'Meldung.',
    at: '2026-10-25T20:00:00Z',
  });

  it('treats missing mappings and errors as failed', () => {
    expect(syncFailed(result('REFRESHED'))).toBe(false);
    expect(syncFailed(result('SCAN_STARTED'))).toBe(false);
    expect(syncFailed(result('NOT_MAPPED'))).toBe(true);
    expect(syncFailed(result('NO_KODI_SOURCE'))).toBe(true);
    expect(syncFailed(result('FAILED'))).toBe(true);
  });

  it('keeps problems on screen until they are closed', () => {
    expect(kodiSyncSnackBarConfig([result('REFRESHED')]).duration).toBe(5000);
    expect(
      kodiSyncSnackBarConfig([result('REFRESHED'), result('NO_KODI_SOURCE')]).duration,
    ).toBeUndefined();
  });

  it('summarizes the results of all hosts', () => {
    expect(kodiSyncSummary([result('REFRESHED'), result('FAILED', 'Küche')])).toBe(
      'Wohnzimmer: Meldung. Küche: Meldung.',
    );
    expect(kodiSyncSummary([])).toBe('Kein Kodi-Gerät eingerichtet – unter „Kodi“ anlegen.');
  });

  it('summarizes the import of the watched status', () => {
    const title = 'Gesehen-Status „Filme“ aus Kodi';
    const result = {
      sourceId: 1,
      host: 'Wohnzimmer',
      updated: 3,
      unchanged: 10,
      kept: 0,
      notInKodi: 2,
      failed: 0,
      failures: [],
    };

    expect(kodiPlaybackJobKey(1)).toBe('kodi-playback:1');
    expect(kodiPlaybackSummary(job({ title, status: 'SUCCEEDED', result }))).toBe(
      'Gesehen-Status „Filme“ aus Kodi (Wohnzimmer) abgeschlossen: 3 übernommen, 10 unverändert, 2 in Kodi unbekannt.',
    );
    expect(
      kodiPlaybackSummary(
        job({ title, status: 'SUCCEEDED', result: { ...result, kept: 16, failed: 1 } }),
      ),
    ).toContain(', 16 abweichend (KMM-Stand behalten), 1 Fehler.');
    expect(kodiPlaybackSummary(job({ title, status: 'CANCELLED' }))).toBe(`${title} abgebrochen.`);
    expect(
      kodiPlaybackSummary(job({ title, status: 'FAILED', message: 'Kodi nicht erreichbar' })),
    ).toBe(`${title} fehlgeschlagen: Kodi nicht erreichbar`);
    expect(kodiPlaybackSummary(job({ title, status: 'FAILED', message: null }))).toContain(
      'Unbekannter Fehler',
    );
  });
});
