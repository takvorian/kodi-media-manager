import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ArtworkInfo } from '../movies/artwork';
import { TvShowArtworkService } from './tvshow-artwork.service';
import { EPISODES_URL, TVSHOWS_URL } from './tvshow.service';

describe('TvShowArtworkService', () => {
  const poster: ArtworkInfo = {
    type: 'POSTER',
    exists: true,
    path: '/s/poster.jpg',
    modifiedAt: '2026-10-26T10:00:00Z',
  };

  let service: TvShowArtworkService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TvShowArtworkService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('addresses the images of the show', () => {
    const show = service.forShow(3, null);
    show.findAll().subscribe();
    show.candidates('POSTER').subscribe();
    show.apply('POSTER', 'https://example.org/p.jpg').subscribe();
    show.remove('POSTER').subscribe();

    httpTesting.expectOne(`${TVSHOWS_URL}/3/artwork`).flush([]);
    httpTesting
      .expectOne(`${TVSHOWS_URL}/3/artwork/POSTER/candidates`)
      .flush({ candidates: [], warnings: [] });
    const apply = httpTesting.expectOne({ method: 'PUT', url: `${TVSHOWS_URL}/3/artwork/POSTER` });
    expect(apply.request.body).toEqual({ url: 'https://example.org/p.jpg' });
    apply.flush(poster);
    httpTesting.expectOne({ method: 'DELETE', url: `${TVSHOWS_URL}/3/artwork/POSTER` }).flush(null);
    expect(show.imageUrl(poster)).toBe(
      `${TVSHOWS_URL}/3/artwork/POSTER/image?v=2026-10-26T10%3A00%3A00Z`,
    );
  });

  it('passes the season to every request', () => {
    const season = service.forShow(3, 0);
    season.findAll().subscribe();
    season.candidates('BANNER').subscribe();
    season.remove('BANNER').subscribe();

    const requests = httpTesting.match((request) =>
      request.url.startsWith(`${TVSHOWS_URL}/3/artwork`),
    );
    expect(requests.map((request) => request.request.params.get('season'))).toEqual([
      '0',
      '0',
      '0',
    ]);
    requests.forEach((request) => request.flush(null));
    expect(season.imageUrl({ ...poster, modifiedAt: null })).toBe(
      `${TVSHOWS_URL}/3/artwork/POSTER/image?season=0`,
    );
    expect(season.imageUrl(poster)).toBe(
      `${TVSHOWS_URL}/3/artwork/POSTER/image?season=0&v=2026-10-26T10%3A00%3A00Z`,
    );
  });

  it('addresses the thumb of an episode', () => {
    service.forEpisode(12).findAll().subscribe();

    httpTesting.expectOne(`${EPISODES_URL}/12/artwork`).flush([]);
  });
});
