import { KodiSyncResult, kodiSyncSummary, syncFailed } from './kodi';

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
    expect(syncFailed(result('FAILED'))).toBe(true);
  });

  it('summarizes the results of all hosts', () => {
    expect(kodiSyncSummary([result('REFRESHED'), result('FAILED', 'Küche')])).toBe(
      'Wohnzimmer: Meldung. Küche: Meldung.',
    );
    expect(kodiSyncSummary([])).toBe('Kein Kodi-Gerät eingerichtet – unter „Kodi“ anlegen.');
  });
});
