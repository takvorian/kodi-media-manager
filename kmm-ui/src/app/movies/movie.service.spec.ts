import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { MovieMetadata, MoviePage } from './movie';
import { METADATA_SCRAPERS_URL, MOVIES_URL, MovieService } from './movie.service';

describe('MovieService', () => {
  const emptyPage: MoviePage = { content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 };

  let service: MovieService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
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

  it('lists the metadata scrapers', () => {
    service.metadataScrapers().subscribe();

    httpTesting.expectOne({ method: 'GET', url: METADATA_SCRAPERS_URL }).flush([]);
  });

  it('searches online with title and year', () => {
    service.searchOnline(5, 'tmdb', ' Heat ', 1995).subscribe();

    const request = httpTesting.expectOne((req) => req.url === `${MOVIES_URL}/5/scraper-search`);
    request.flush([]);

    expect(request.request.params.get('scraper')).toBe('tmdb');
    expect(request.request.params.get('title')).toBe('Heat');
    expect(request.request.params.get('year')).toBe('1995');
  });

  it('searches online with the movie data when no title is given', () => {
    service.searchOnline(5, 'tmdb', '  ', 1995).subscribe();

    const request = httpTesting.expectOne((req) => req.url === `${MOVIES_URL}/5/scraper-search`);
    request.flush([]);

    expect(request.request.params.has('title')).toBe(false);
    expect(request.request.params.has('year')).toBe(false);
  });

  it('searches online with a title but without year', () => {
    service.searchOnline(5, 'tmdb', 'Heat', null).subscribe();

    const request = httpTesting.expectOne((req) => req.url === `${MOVIES_URL}/5/scraper-search`);
    request.flush([]);

    expect(request.request.params.has('year')).toBe(false);
  });

  it('queries movies with a filter and loads the filter values', () => {
    const filter = {
      matchAll: false,
      conditions: [{ field: 'TAG' as const, operator: 'IS' as const, value: 'X' }],
    };
    service.queryMovies(filter, 1, 25).subscribe();
    const query = httpTesting.expectOne((req) => req.url === `${MOVIES_URL}/query`);
    expect(query.request.method).toBe('POST');
    expect(query.request.body).toEqual(filter);
    expect(query.request.params.get('page')).toBe('1');
    expect(query.request.params.get('sort')).toBe('title,asc');
    query.flush({});

    service.filterValues().subscribe();
    httpTesting.expectOne(`${MOVIES_URL}/filter-values`).flush({});
  });

  it('loads the IDs of filtered movies and starts a batch edit', () => {
    service.queryMovieIds({ matchAll: true, conditions: [] }).subscribe();
    httpTesting.expectOne({ method: 'POST', url: `${MOVIES_URL}/query/ids` }).flush([1]);

    const keep = { clear: false, value: null };
    const edit = {
      movieIds: [1],
      genres: { add: [], remove: [] },
      tags: { add: ['Neu'], remove: [] },
      mpaa: keep,
      edition: keep,
      videoSource: keep,
    };
    service.batchEdit(edit).subscribe();
    const batch = httpTesting.expectOne({ method: 'POST', url: `${MOVIES_URL}/batch` });
    expect(batch.request.body).toEqual(edit);
    batch.flush({});
  });

  it('loads a proposal for a search result', () => {
    service.proposal(5, 'tmdb', '949').subscribe();

    const request = httpTesting.expectOne((req) => req.url === `${MOVIES_URL}/5/scraper-proposal`);
    request.flush({});

    expect(request.request.params.get('providerId')).toBe('949');
  });

  it('assigns the collection of a scraped movie', () => {
    service.assignCollection(5, { providerId: '1', name: 'Heat-Reihe' }).subscribe();

    const request = httpTesting.expectOne({ method: 'POST', url: `${MOVIES_URL}/5/collection` });
    expect(request.request.body).toEqual({ providerId: '1', name: 'Heat-Reihe' });
    request.flush({ assigned: true });
  });

  it('saves the metadata of a movie', () => {
    const metadata = { title: 'Heat', genres: ['Krimi'] } as unknown as MovieMetadata;

    service.updateMetadata(5, metadata).subscribe();
    const request = httpTesting.expectOne({ method: 'PUT', url: `${MOVIES_URL}/5/metadata` });
    request.flush({});

    expect(request.request.body).toEqual(metadata);
  });
});
