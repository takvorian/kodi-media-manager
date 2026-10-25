import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { MovieMetadata, MoviePage } from './movie';
import { MOVIES_URL, MovieService } from './movie.service';

describe('MovieService', () => {
  const emptyPage: MoviePage = { content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 };

  let service: MovieService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(MovieService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('requests a page sorted by title', () => {
    let received: MoviePage | undefined;

    service.findMovies({ search: '', page: 2, size: 25 }).subscribe((page) => (received = page));
    const request = httpTesting.expectOne((req) => req.url === MOVIES_URL);
    request.flush(emptyPage);

    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('25');
    expect(request.request.params.get('sort')).toBe('title,asc');
    expect(request.request.params.has('search')).toBe(false);
    expect(received).toEqual(emptyPage);
  });

  it('sends a trimmed search term', () => {
    service.findMovies({ search: '  matrix ', page: 0, size: 50 }).subscribe();

    const request = httpTesting.expectOne((req) => req.url === MOVIES_URL);
    request.flush(emptyPage);

    expect(request.request.params.get('search')).toBe('matrix');
  });

  it('loads the details of a movie', () => {
    service.findMovie(5).subscribe();

    httpTesting.expectOne({ method: 'GET', url: `${MOVIES_URL}/5` }).flush({});
  });

  it('saves the metadata of a movie', () => {
    const metadata = { title: 'Heat', genres: ['Krimi'] } as unknown as MovieMetadata;

    service.updateMetadata(5, metadata).subscribe();
    const request = httpTesting.expectOne({ method: 'PUT', url: `${MOVIES_URL}/5/metadata` });
    request.flush({});

    expect(request.request.body).toEqual(metadata);
  });
});
