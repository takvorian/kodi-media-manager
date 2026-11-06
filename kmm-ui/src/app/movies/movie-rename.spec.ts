import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';

import { provideI18nTesting } from '../i18n/testing';
import { Job } from '../jobs/job';
import {
  MovieRenamePlan,
  movieRenameJobKey,
  movieRenameSummary,
  renameConfirmation,
} from './movie-rename';
import { MovieRenameService } from './movie-rename.service';

describe('movie rename', () => {
  const plan: MovieRenamePlan = {
    movieId: 5,
    title: 'Heat',
    currentDirectory: '/filme/heat',
    targetDirectory: '/filme/heat',
    renames: [{ currentName: 'heat.mkv', targetName: 'Heat.mkv' }],
    status: 'RENAME',
    message: null,
  };

  let transloco: TranslocoService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideI18nTesting()],
    });
    transloco = TestBed.inject(TranslocoService);
  });

  it('describes the new names for confirmation', () => {
    expect(renameConfirmation(plan)).toBe('„Heat“ umbenennen?\n\nDateien:\nheat.mkv → Heat.mkv');
    expect(renameConfirmation({ ...plan, targetDirectory: '/filme/Heat', renames: [] })).toBe(
      '„Heat“ umbenennen?\n\nOrdner: /filme/heat\n→ /filme/Heat',
    );
  });

  it('describes the new names in the active language', () => {
    transloco.setActiveLang('en');

    expect(
      renameConfirmation({ ...plan, targetDirectory: '/filme/Heat', renames: plan.renames }),
    ).toBe('Rename “Heat”?\n\nFolder: /filme/heat\n→ /filme/Heat\n\nFiles:\nheat.mkv → Heat.mkv');
    expect(
      movieRenameSummary({
        id: 'j',
        key: movieRenameJobKey(3),
        title: 'Rename movies',
        status: 'FAILED',
        message: null,
      } as Job),
    ).toBe('Rename movies failed: Unknown error');
  });

  it('summarizes finished jobs', () => {
    const job = {
      id: 'j',
      key: movieRenameJobKey(3),
      title: 'Filme umbenennen',
      status: 'SUCCEEDED',
    } as Job;

    expect(
      movieRenameSummary({
        ...job,
        result: { sourceId: 3, renamed: 2, unchanged: 1, skipped: 0, failed: 1, problems: [] },
      }),
    ).toBe(
      'Filme umbenennen abgeschlossen: 2 umbenannt, 1 unverändert, 0 übersprungen, 1 fehlgeschlagen.',
    );
    expect(movieRenameSummary({ ...job, status: 'CANCELLED' })).toBe(
      'Filme umbenennen abgebrochen.',
    );
    expect(movieRenameSummary({ ...job, status: 'FAILED', message: 'kaputt' })).toBe(
      'Filme umbenennen fehlgeschlagen: kaputt',
    );
    expect(movieRenameSummary({ ...job, status: 'FAILED', message: null })).toBe(
      'Filme umbenennen fehlgeschlagen: Unbekannter Fehler',
    );
    expect(job.key).toBe('movie-rename:3');
  });

  it('calls the rename endpoints', () => {
    const service = TestBed.inject(MovieRenameService);
    const httpTesting = TestBed.inject(HttpTestingController);
    const patterns = { folder: '{title}', file: '{title}' };

    service.previewSource(3, patterns).subscribe();
    service.renameSource(3, patterns, [5]).subscribe();
    service.previewMovie(5, patterns).subscribe();
    service.renameMovie(5, patterns).subscribe();

    httpTesting
      .expectOne({ method: 'POST', url: '/api/v1/sources/3/movie-rename/preview' })
      .flush([]);
    expect(
      httpTesting.expectOne({ method: 'POST', url: '/api/v1/sources/3/movie-rename' }).request.body,
    ).toEqual({ patterns, movieIds: [5] });
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/movies/5/rename/preview' }).flush(plan);
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/movies/5/rename' }).flush(plan);
    httpTesting.verify({ ignoreCancelled: true });
  });
});
