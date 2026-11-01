import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Job } from '../jobs/job';
import { MediaSource } from './media-source';
import { MediaSourceService, SOURCES_URL } from './media-source.service';

describe('MediaSourceService', () => {
  const source: MediaSource = {
    id: 1,
    name: 'Filme',
    path: '/media/filme',
    mediaType: 'MOVIE',
    createdAt: '2026-10-25T10:00:00Z',
    lastScannedAt: null,
    metadataLanguage: null,
  };

  let service: MediaSourceService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
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
    const command = {
      name: 'Filme',
      path: '/media/filme',
      mediaType: 'MOVIE' as const,
      metadataLanguage: 'de-DE',
    };

    service.create(command).subscribe();
    const request = httpTesting.expectOne({ method: 'POST', url: SOURCES_URL });
    request.flush(source);

    expect(request.request.body).toEqual(command);
  });

  it('changes the language of a source', () => {
    service.changeLanguage(7, null).subscribe();

    const request = httpTesting.expectOne({ method: 'PUT', url: `${SOURCES_URL}/7/language` });
    expect(request.request.body).toEqual({ language: null });
    request.flush(source);
  });

  it('deletes a source', () => {
    service.delete(7).subscribe();

    httpTesting
      .expectOne({ method: 'DELETE', url: `${SOURCES_URL}/7` })
      .flush(null, { status: 204, statusText: 'No Content' });
  });

  it('starts a scan job', () => {
    const job = { id: 'job-1', key: 'scan:7', status: 'RUNNING' } as Job;
    let received: Job | undefined;

    service.scan(7).subscribe((started) => (received = started));
    httpTesting.expectOne({ method: 'POST', url: `${SOURCES_URL}/7/scan` }).flush(job);

    expect(received).toEqual(job);
  });

  it('starts an auto-scrape job', () => {
    const job = { id: 'job-2', key: 'auto-scrape:7', status: 'RUNNING' } as Job;
    let received: Job | undefined;

    service.autoScrape(7).subscribe((started) => (received = started));
    httpTesting.expectOne({ method: 'POST', url: `${SOURCES_URL}/7/auto-scrape` }).flush(job);

    expect(received).toEqual(job);
  });
});
