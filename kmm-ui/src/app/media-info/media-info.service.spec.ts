import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { EMPTY_STREAMS, StreamDetails } from '../shared/stream-details';
import { MediaInfoService } from './media-info.service';

describe('MediaInfoService', () => {
  let service: MediaInfoService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(MediaInfoService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('reads a source, a movie and an episode', () => {
    let movie: StreamDetails | undefined;

    service.probeSource(4, 'ALL').subscribe();
    service.probeMovie(5).subscribe((details) => (movie = details));
    service.probeEpisode(6).subscribe();
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/sources/4/stream-details?scope=ALL' }).flush({});
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/movies/5/stream-details' }).flush(EMPTY_STREAMS);
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/episodes/6/stream-details' }).flush(EMPTY_STREAMS);

    expect(movie).toEqual(EMPTY_STREAMS);
  });
});
