import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ArtworkService } from './artwork.service';
import { MOVIES_URL } from './movie.service';

describe('ArtworkService', () => {
  let service: ArtworkService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ArtworkService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('loads the artwork of a movie', () => {
    service.findAll(5).subscribe();

    httpTesting.expectOne({ method: 'GET', url: `${MOVIES_URL}/5/artwork` }).flush([]);
  });

  it('builds image urls that change with the file', () => {
    const poster = { type: 'POSTER' as const, exists: true, path: '/x', modifiedAt: '2026-10-25T12:00:00Z' };

    expect(service.imageUrl(5, poster)).toBe(`${MOVIES_URL}/5/artwork/POSTER/image?v=2026-10-25T12%3A00%3A00Z`);
    expect(service.imageUrl(5, { ...poster, modifiedAt: null })).toBe(`${MOVIES_URL}/5/artwork/POSTER/image`);
  });

  it('loads candidates of an artwork type', () => {
    service.candidates(5, 'FANART').subscribe();

    httpTesting.expectOne({ method: 'GET', url: `${MOVIES_URL}/5/artwork/FANART/candidates` }).flush({});
  });

  it('applies an image by url', () => {
    service.apply(5, 'POSTER', 'https://img/p.jpg').subscribe();

    const request = httpTesting.expectOne({ method: 'PUT', url: `${MOVIES_URL}/5/artwork/POSTER` });
    request.flush({});

    expect(request.request.body).toEqual({ url: 'https://img/p.jpg' });
  });

  it('removes an image', () => {
    service.remove(5, 'BANNER').subscribe();

    httpTesting.expectOne({ method: 'DELETE', url: `${MOVIES_URL}/5/artwork/BANNER` }).flush(null);
  });
});
