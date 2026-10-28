import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Job } from '../jobs/job';
import {
  TvShowRenamePlan,
  folderLabel,
  tvRenameConfirmation,
  tvRenameJobKey,
  tvRenameSummary,
} from './tv-rename';
import { TvRenameService } from './tv-rename.service';

describe('tv rename', () => {
  const plan: TvShowRenamePlan = {
    showId: 2,
    title: 'Lost',
    currentDirectory: '/serien/Lost',
    targetDirectory: '/serien/Lost',
    files: [
      {
        episodes: 'S01E01',
        currentFolder: '',
        targetFolder: 'Staffel 1',
        renames: [],
        status: 'RENAME',
        message: null,
      },
    ],
    status: 'RENAME',
    message: null,
  };

  it('describes the changes for confirmation', () => {
    expect(tvRenameConfirmation(plan)).toBe(
      '„Lost“ umbenennen?\n\n1 Episodendateien bekommen neue Namen bzw. Staffelordner.',
    );
    expect(tvRenameConfirmation({ ...plan, targetDirectory: '/serien/LOST', files: [] })).toBe(
      '„Lost“ umbenennen?\n\nOrdner: /serien/Lost\n→ /serien/LOST',
    );
    expect(folderLabel('')).toBe('(Serienordner)');
    expect(folderLabel('Staffel 1')).toBe('Staffel 1');
  });

  it('summarizes finished jobs', () => {
    const job = {
      id: 'j',
      key: tvRenameJobKey(8),
      title: 'Serien umbenennen',
      status: 'SUCCEEDED',
    } as Job;

    expect(
      tvRenameSummary({
        ...job,
        result: { sourceId: 8, renamed: 2, unchanged: 1, skipped: 0, failed: 1, problems: [] },
      }),
    ).toBe(
      'Serien umbenennen abgeschlossen: 2 umbenannt, 1 unverändert, 0 übersprungen, 1 fehlgeschlagen.',
    );
    expect(tvRenameSummary({ ...job, status: 'CANCELLED' })).toBe('Serien umbenennen abgebrochen.');
    expect(tvRenameSummary({ ...job, status: 'FAILED', message: null })).toBe(
      'Serien umbenennen fehlgeschlagen: Unbekannter Fehler',
    );
    expect(job.key).toBe('tv-rename:8');
  });

  it('calls the rename endpoints', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const service = TestBed.inject(TvRenameService);
    const httpTesting = TestBed.inject(HttpTestingController);
    const patterns = { showFolder: '{show}', seasonFolder: '{season}', episodeFile: '{show}' };

    service.previewSource(8, patterns).subscribe();
    service.renameSource(8, patterns, [2]).subscribe();
    service.previewShow(2, patterns).subscribe();
    service.renameShow(2, patterns).subscribe();

    httpTesting.expectOne({ method: 'POST', url: '/api/v1/sources/8/tv-rename/preview' }).flush([]);
    expect(
      httpTesting.expectOne({ method: 'POST', url: '/api/v1/sources/8/tv-rename' }).request.body,
    ).toEqual({
      patterns,
      showIds: [2],
    });
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/tvshows/2/rename/preview' }).flush(plan);
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/tvshows/2/rename' }).flush(plan);
  });
});
