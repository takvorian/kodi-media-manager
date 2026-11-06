import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';

import { provideI18nTesting } from '../i18n/testing';
import { job } from '../jobs/fake-job-events';
import {
  KodiPlaybackResult,
  KodiSyncResult,
  kodiPlaybackJobKey,
  kodiPlaybackMessage,
  kodiPlaybackSummary,
  kodiSyncSnackBarConfig,
  kodiSyncSummary,
  syncFailed,
} from './kodi';
import { literal } from '../i18n/localized-text';

describe('kodi', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideI18nTesting()] });
    TestBed.inject(TranslocoService);
  });

  const result = (outcome: KodiSyncResult['outcome'], host = 'Wohnzimmer'): KodiSyncResult => ({
    hostId: 1,
    host,
    outcome,
    message: literal('Meldung.'),
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

  it('translates the messages of the server', () => {
    const scan: KodiSyncResult = {
      ...result('SCAN_STARTED'),
      message: {
        key: 'kodi.sync.scanSource',
        args: { directory: 'smb://nas/serien/', kind: 'tvShow' },
      },
    };
    const playback = (args: Record<string, unknown>): KodiPlaybackResult => ({
      host: 'Wohnzimmer',
      outcome: 'UPDATED',
      message: { key: 'kodi.playback.updated', args: { label: 'Heat', ...args } },
      playCount: null,
      lastPlayedAt: null,
      userRating: null,
    });

    expect(kodiSyncSummary([scan])).toBe(
      'Wohnzimmer: Kodi sucht in smb://nas/serien/ nach neuen Serien und Episoden.',
    );
    expect(kodiPlaybackMessage(playback({ playCount: 2, rating: 8 }))).toBe(
      'Wohnzimmer: „Heat“ aus Kodi übernommen: 2× gesehen, Bewertung 8.',
    );
    expect(kodiPlaybackMessage(playback({ playCount: 0, rating: 'none' }))).toBe(
      'Wohnzimmer: „Heat“ aus Kodi übernommen: nicht gesehen.',
    );
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
    expect(kodiPlaybackSummary(job({ title: literal(title), status: 'SUCCEEDED', result }))).toBe(
      'Gesehen-Status „Filme“ aus Kodi (Wohnzimmer) abgeschlossen: 3 übernommen, 10 unverändert, 2 in Kodi unbekannt.',
    );
    expect(
      kodiPlaybackSummary(
        job({
          title: literal(title),
          status: 'SUCCEEDED',
          result: { ...result, kept: 16, failed: 1 },
        }),
      ),
    ).toContain(', 16 abweichend (KMM-Stand behalten), 1 Fehler.');
    expect(kodiPlaybackSummary(job({ title: literal(title), status: 'CANCELLED' }))).toBe(
      `${title} abgebrochen.`,
    );
    expect(
      kodiPlaybackSummary(
        job({ title: literal(title), status: 'FAILED', message: literal('Kodi nicht erreichbar') }),
      ),
    ).toBe(`${title} fehlgeschlagen: Kodi nicht erreichbar`);
    expect(
      kodiPlaybackSummary(job({ title: literal(title), status: 'FAILED', message: null })),
    ).toContain('Unbekannter Fehler');
  });

  it('summarizes in the active UI language', () => {
    TestBed.inject(TranslocoService).setActiveLang('en');
    const result = {
      sourceId: 1,
      host: 'Living room',
      updated: 3,
      unchanged: 10,
      kept: 2,
      notInKodi: 0,
      failed: 1,
      failures: [literal('x')],
    };

    expect(kodiSyncSummary([])).toBe('No Kodi device set up – add one under “Kodi”.');
    expect(
      kodiPlaybackSummary(job({ title: literal('Watched status'), status: 'SUCCEEDED', result })),
    ).toBe(
      'Watched status (Living room) finished: 3 taken over, 10 unchanged, 0 unknown in Kodi, ' +
        '2 different (KMM state kept), 1 error.',
    );
    expect(kodiPlaybackSummary(job({ title: literal('X'), status: 'FAILED', message: null }))).toBe(
      'X failed: Unknown error',
    );
  });
});
