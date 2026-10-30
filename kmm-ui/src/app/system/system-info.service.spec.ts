import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { SystemInfo } from './system-info';
import { CHANGELOG_URL, SYSTEM_INFO_URL, SystemInfoService } from './system-info.service';

describe('SystemInfoService', () => {
  const systemInfo: SystemInfo = {
    applicationName: 'Kodi Media Manager',
    version: '0.1.0',
    javaVersion: '25',
    activeProfiles: ['standalone'],
    database: 'H2',
  };

  let service: SystemInfoService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SystemInfoService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('loads the system info from the backend', () => {
    let received: SystemInfo | undefined;

    service.load().subscribe((info) => (received = info));
    const request = httpTesting.expectOne(SYSTEM_INFO_URL);
    request.flush(systemInfo);

    expect(request.request.method).toBe('GET');
    expect(received).toEqual(systemInfo);
  });

  it('loads the changelog from the backend', () => {
    const changelog = [{ version: 'v0.1', date: '2026-10-30', changes: ['Erste (abc1234)'] }];
    let received: unknown;

    service.changelog().subscribe((versions) => (received = versions));
    httpTesting.expectOne(CHANGELOG_URL).flush(changelog);

    expect(received).toEqual(changelog);
  });

  it('propagates backend errors', () => {
    let failed = false;

    service.load().subscribe({ error: () => (failed = true) });
    httpTesting
      .expectOne(SYSTEM_INFO_URL)
      .flush('boom', { status: 500, statusText: 'Server Error' });

    expect(failed).toBe(true);
  });
});
