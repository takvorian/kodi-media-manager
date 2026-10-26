import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { EpisodeMetadata, TvShowMetadata, episodeCode, seasonLabel } from './tvshow';
import { EPISODES_URL, TVSHOWS_URL, TvShowService } from './tvshow.service';

describe('TvShowService', () => {
  let service: TvShowService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(TvShowService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('loads shows with and without search', () => {
    service.findShows({ search: ' alf ', page: 1, size: 25 }).subscribe();
    service.findShows({ search: '', page: 0, size: 50 }).subscribe();

    const requests = httpTesting.match((request) => request.url === TVSHOWS_URL);
    expect(requests.map((request) => request.request.params.get('search'))).toEqual(['alf', null]);
    expect(requests[0].request.params.get('page')).toBe('1');
    requests.forEach((request) => request.flush({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 }));
  });

  it('reads and updates shows and episodes', () => {
    service.findShow(3).subscribe();
    service.updateShow(3, { title: 'ALF' } as TvShowMetadata).subscribe();
    service.findEpisode(12).subscribe();
    service.updateEpisode(12, { title: 'Pilot' } as EpisodeMetadata).subscribe();

    httpTesting.expectOne({ method: 'GET', url: `${TVSHOWS_URL}/3` }).flush({});
    httpTesting.expectOne({ method: 'PUT', url: `${TVSHOWS_URL}/3/metadata` }).flush({});
    httpTesting.expectOne({ method: 'GET', url: `${EPISODES_URL}/12` }).flush({});
    httpTesting.expectOne({ method: 'PUT', url: `${EPISODES_URL}/12/metadata` }).flush({});
  });

  it('formats episode codes and season labels', () => {
    expect(episodeCode(1, 4)).toBe('S01E04');
    expect(episodeCode(1970, 112)).toBe('S1970E112');
    expect(seasonLabel({ number: 0, title: null, episodeCount: 1 })).toBe('Specials');
    expect(seasonLabel({ number: 2, title: 'Staffel 2', episodeCount: 1 })).toBe('Staffel 2');
    expect(seasonLabel({ number: 2, title: 'Rückkehr', episodeCount: 1 })).toBe('Staffel 2 – Rückkehr');
  });
});
