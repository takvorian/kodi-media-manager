import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { MovieFilter } from './movie-filter';
import { FILTERS_URL, SavedFilterService } from './saved-filter.service';

describe('SavedFilterService', () => {
  const definition: MovieFilter = { matchAll: true, conditions: [] };

  let service: SavedFilterService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SavedFilterService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('lists, creates, updates and deletes saved filters', () => {
    service.findAll('MOVIE').subscribe();
    httpTesting.expectOne(`${FILTERS_URL}?mediaType=MOVIE`).flush([]);

    service.create('MOVIE', 'Alle', definition).subscribe();
    const created = httpTesting.expectOne({ method: 'POST', url: FILTERS_URL });
    expect(created.request.body).toEqual({ mediaType: 'MOVIE', name: 'Alle', definition });
    created.flush({});

    service.update(3, 'MOVIE', 'Alle', definition).subscribe();
    httpTesting.expectOne({ method: 'PUT', url: `${FILTERS_URL}/3` }).flush({});

    service.delete(3).subscribe();
    httpTesting.expectOne({ method: 'DELETE', url: `${FILTERS_URL}/3` }).flush(null);
  });
});
