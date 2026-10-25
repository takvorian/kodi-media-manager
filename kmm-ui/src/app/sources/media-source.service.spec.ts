import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { MediaSource, ScanResult } from './media-source';
import { MediaSourceService, SOURCES_URL } from './media-source.service';

describe('MediaSourceService', () => {
  const source: MediaSource = {
    id: 1,
    name: 'Filme',
    path: '/media/filme',
    mediaType: 'MOVIE',
    createdAt: '2026-10-25T10:00:00Z',
    lastScannedAt: null,
  };

  let service: MediaSourceService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(MediaSourceService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('loads all sources', () => {
    let received: MediaSource[] | undefined;

    service.findAll().subscribe((sources) => (received = sources));
    httpTesting.expectOne({ method: 'GET', url: SOURCES_URL }).flush([source]);

    expect(received).toEqual([source]);
  });

  it('creates a source', () => {
    const command = { name: 'Filme', path: '/media/filme', mediaType: 'MOVIE' as const };

    service.create(command).subscribe();
    const request = httpTesting.expectOne({ method: 'POST', url: SOURCES_URL });
    request.flush(source);

    expect(request.request.body).toEqual(command);
  });

  it('deletes a source', () => {
    service.delete(7).subscribe();

    httpTesting.expectOne({ method: 'DELETE', url: `${SOURCES_URL}/7` }).flush(null, { status: 204, statusText: 'No Content' });
  });

  it('scans a source', () => {
    const result: ScanResult = { sourceId: 7, added: 2, removed: 0, unchanged: 5 };
    let received: ScanResult | undefined;

    service.scan(7).subscribe((scan) => (received = scan));
    httpTesting.expectOne({ method: 'POST', url: `${SOURCES_URL}/7/scan` }).flush(result);

    expect(received).toEqual(result);
  });
});
