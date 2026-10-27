import { Job } from '../jobs/job';
import {
  newFilesStreamDetailsJobKey,
  showStreamDetailsSummary,
  streamDetailsJobKey,
  streamDetailsSummary,
} from './media-info';

describe('media info', () => {
  const job: Job = {
    id: 'j',
    key: 'stream-details:1',
    title: 'Technische Angaben „Filme“',
    status: 'SUCCEEDED',
    done: 3,
    total: 3,
    message: null,
    result: { sourceId: 1, updated: 2, skipped: 5, failed: 1, failures: ['Heat: kaputt'] },
    startedAt: '2026-10-27T10:00:00Z',
    finishedAt: '2026-10-27T10:01:00Z',
  };

  it('builds job keys', () => {
    expect(streamDetailsJobKey(1)).toBe('stream-details:1');
    expect(newFilesStreamDetailsJobKey(1)).toBe('stream-details-new:1');
  });

  it('summarizes finished jobs', () => {
    expect(streamDetailsSummary(job)).toBe(
      'Technische Angaben „Filme“ abgeschlossen: 2 ausgelesen, 5 hatten schon Angaben, 1 Fehler (z. B. Heat: kaputt).',
    );
    expect(streamDetailsSummary({ ...job, result: { updated: 1, skipped: 0, failed: 0, failures: [] } })).toBe(
      'Technische Angaben „Filme“ abgeschlossen: 1 ausgelesen.',
    );
    expect(streamDetailsSummary({ ...job, status: 'CANCELLED' })).toBe('Technische Angaben „Filme“ abgebrochen.');
    expect(streamDetailsSummary({ ...job, status: 'FAILED', message: 'weg' })).toBe(
      'Technische Angaben „Filme“ fehlgeschlagen: weg',
    );
  });

  it('summarizes the episodes read after the online search', () => {
    expect(showStreamDetailsSummary(null)).toBeNull();
    expect(showStreamDetailsSummary({ updated: 0, failures: [] })).toBeNull();
    expect(showStreamDetailsSummary({ updated: 3, failures: [] })).toBe(
      'Technische Angaben von 3 Episodendateien ausgelesen.',
    );
    expect(showStreamDetailsSummary({ updated: 0, failures: ['kaputt'] })).toBe(
      'Technische Angaben von 0 Episodendateien ausgelesen. 1 Fehler (z. B. kaputt).',
    );
  });
});
