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
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(EmberMigrationService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('inspects a profile', () => {
    let received: EmberInspection | undefined;

    service.inspect('C:\\Ember').subscribe((inspection) => (received = inspection));
    const request = httpTesting.expectOne((req) => req.url === `${EMBER_MIGRATION_URL}/inspect`);
    request.flush({ schemaVersion: 48 });

    expect(request.request.params.get('path')).toBe('C:\\Ember');
    expect(received?.schemaVersion).toBe(48);
  });

  it('starts an import job', () => {
    const importRequest: EmberImportRequest = {
      profilePath: 'C:\\Ember',
      pathMappings: [{ emberPath: 'a:\\filme', kmmPath: '/media/filme' }],
      importSettings: true,
      importKodiHosts: true,
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
