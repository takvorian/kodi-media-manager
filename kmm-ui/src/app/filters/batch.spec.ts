import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';

import { provideI18nTesting } from '../i18n/testing';
import { job } from '../jobs/fake-job-events';
import { switchLanguage } from '../shared/testing';
import { BatchResult, batchSummary } from './batch';

describe('batchSummary', () => {
  const title = 'Stapel-Bearbeitung von 3 Filmen';
  const result: BatchResult = { changed: 2, unchanged: 1, failed: 0, failures: [] };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideI18nTesting()] });
    TestBed.inject(TranslocoService);
  });

  it('sums up a finished job', () => {
    expect(batchSummary(job({ title, status: 'SUCCEEDED', result }))).toBe(
      `${title}: 2 geändert, 1 unverändert.`,
    );
    expect(
      batchSummary(
        job({
          title,
          status: 'SUCCEEDED',
          result: { ...result, failed: 1, failures: ['Heat: NFO gesperrt.'] },
        }),
      ),
    ).toBe(`${title}: 2 geändert, 1 unverändert, 1 Fehler: Heat: NFO gesperrt.`);
  });

  it('reports cancelled and failed jobs', () => {
    expect(batchSummary(job({ title, status: 'CANCELLED' }))).toBe(`${title} abgebrochen.`);
    expect(batchSummary(job({ title, status: 'FAILED', message: 'weg' }))).toBe(
      `${title} fehlgeschlagen: weg`,
    );
    expect(batchSummary(job({ title, status: 'FAILED' }))).toBe(
      `${title} fehlgeschlagen: Unbekannter Fehler`,
    );
  });

  it('sums up in English after a switch, keeping the title from the backend', async () => {
    await switchLanguage('en');

    expect(
      batchSummary(
        job({
          title,
          status: 'SUCCEEDED',
          result: { ...result, failed: 2, failures: ['Heat', 'Ronin'] },
        }),
      ),
    ).toBe(`${title}: 2 changed, 1 unchanged, 2 errors: Heat; Ronin.`);
    expect(batchSummary(job({ title, status: 'CANCELLED' }))).toBe(`${title} cancelled.`);
    expect(batchSummary(job({ title, status: 'FAILED' }))).toBe(`${title} failed: Unknown error`);
  });
});
