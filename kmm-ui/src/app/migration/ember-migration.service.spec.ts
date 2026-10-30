import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Job } from '../jobs/job';
import { EmberImportRequest, EmberInspection } from './ember-migration';
import { EMBER_MIGRATION_URL, EmberMigrationService } from './ember-migration.service';

describe('EmberMigrationService', () => {
  let service: EmberMigrationService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(EmberMigrationService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('uploads the profile files without their folders', () => {
    let received: EmberInspection | undefined;
    const file = new File(['x'], 'MyVideos48.emm');

    service.upload([file]).subscribe((inspection) => (received = inspection));
    const request = httpTesting.expectOne({ method: 'POST', url: `${EMBER_MIGRATION_URL}/upload` });
    request.flush({ schemaVersion: 48 });

    const sent = (request.request.body as FormData).getAll('files') as File[];
    expect(sent.map((part) => part.name)).toEqual(['MyVideos48.emm']);
    expect(received?.schemaVersion).toBe(48);
  });

  it('discards the upload', () => {
    let done = false;

    service.discardUpload().subscribe(() => (done = true));
    httpTesting.expectOne({ method: 'DELETE', url: `${EMBER_MIGRATION_URL}/upload` }).flush(null);

    expect(done).toBe(true);
  });

  it('starts an import job', () => {
    const importRequest: EmberImportRequest = {
      pathMappings: [{ emberPath: 'a:\\filme', kmmPath: '/media/filme' }],
      importSettings: true,
      importKodiHosts: true,
      importTvShows: true,
      dryRun: true,
    };
    let received: Job | undefined;

    service.startImport(importRequest).subscribe((job) => (received = job));
    const request = httpTesting.expectOne({ method: 'POST', url: `${EMBER_MIGRATION_URL}/import` });
    request.flush({ id: 'job-1', key: 'ember-import' });

    expect(request.request.body).toEqual(importRequest);
    expect(received?.key).toBe('ember-import');
  });
});
