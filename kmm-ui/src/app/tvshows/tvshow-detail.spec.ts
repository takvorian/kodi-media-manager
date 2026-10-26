import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';

import { TvShowDetails } from './tvshow';
import { TvShowDetail } from './tvshow-detail';
import { TVSHOWS_URL } from './tvshow.service';

describe('TvShowDetail', () => {
  const alf: TvShowDetails = {
    id: 3,
    sourceId: 2,
    directoryPath: '/media/serien/ALF (1986)',
    addedAt: '2026-10-26T10:00:00Z',
    metadata: {
      title: 'ALF',
      originalTitle: null,
      sortTitle: null,
      year: 1986,
      premiered: '1986-09-22',
      plot: 'Ein Außerirdischer.',
      runtime: 30,
      mpaa: 'FSK 6',
      status: 'Ended',
      genres: ['Komödie', 'Familie'],
      studios: ['NBC'],
      tags: [],
      tvdbId: '78020',
      tmdbId: null,
      imdbId: 'tt0090390',
      userRating: null,
    },
    extras: {
      actors: [{ name: 'Paul Fusco', role: 'ALF', thumb: null }],
      ratings: [{ source: 'tvdb', value: 7.3, max: 10, votes: 38, defaultRating: true }],
      seasonNames: { '1': 'Ankunft' },
    },
    seasons: [
      { number: 0, title: null, episodeCount: 1 },
      { number: 1, title: 'Ankunft', episodeCount: 2 },
      { number: 2, title: null, episodeCount: 0 },
    ],
    episodes: [
      { id: 10, season: 0, episode: 1, title: 'Making of', aired: null, filePath: '/x0.avi', playCount: null },
      { id: 11, season: 1, episode: 2, title: 'Der Fremde', aired: '1986-09-29', filePath: '/x1.avi', playCount: 1 },
      { id: 12, season: 1, episode: 16, title: 'Gestatten', aired: null, filePath: '/x2.avi', playCount: null },
    ],
    nfo: { path: '/media/serien/ALF (1986)/tvshow.nfo', exists: true, modifiedAt: '2026-10-26T09:00:00Z' },
  };

  let fixture: ComponentFixture<TvShowDetail>;
  let httpTesting: HttpTestingController;
  let snackBarMessages: string[];

  beforeEach(() => {
    snackBarMessages = [];
    TestBed.configureTestingModule({
      imports: [TvShowDetail],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: MatSnackBar, useValue: { open: (message: string) => snackBarMessages.push(message) } },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TvShowDetail);
    fixture.componentRef.setInput('id', '3');
  });

  afterEach(() => httpTesting.verify());

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function input(name: string): HTMLInputElement {
    return element().querySelector<HTMLInputElement>(`[name="${name}"]`)!;
  }

  function texts(selector: string): (string | undefined)[] {
    return Array.from(element().querySelectorAll(selector)).map((item) => item.textContent?.replace(/\s+/g, ' ').trim());
  }

  async function load(details: TvShowDetails): Promise<void> {
    fixture.detectChanges();
    httpTesting.expectOne({ method: 'GET', url: `${TVSHOWS_URL}/3` }).flush(details);
    await fixture.whenStable();
  }

  async function type(name: string, value: string): Promise<void> {
    input(name).value = value;
    input(name).dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  it('shows the show with seasons, episodes and people', async () => {
    await load(alf);

    expect(element().querySelector('h2')?.textContent).toBe('ALF');
    expect(element().querySelector('.directory')?.textContent).toBe('/media/serien/ALF (1986)');
    expect(texts('.season-title')).toEqual(['Specials', 'Staffel 1 – Ankunft']);
    expect(texts('.episode .code')).toEqual(['S00E01', 'S01E02', 'S01E16']);
    expect(element().querySelector('.episode a')?.getAttribute('href')).toBe('/episodes/10');
    expect(element().querySelectorAll('.watched')).toHaveLength(1);
    expect(element().textContent).toContain('Paul Fusco');
    expect(element().textContent).toContain('TVDB 7.3/10');
    expect(input('genres').value).toBe('Komödie, Familie');
    expect(input('tvdbId').value).toBe('78020');
  });

  it('saves the metadata and reports where the NFO was written', async () => {
    await load({ ...alf, nfo: { ...alf.nfo, exists: false, modifiedAt: null }, seasons: [], episodes: [] });
    expect(element().querySelector('.nfo-status')?.textContent).toContain('Noch keine tvshow.nfo');
    expect(element().querySelector('.empty-seasons')).not.toBeNull();

    await type('title', ' ALF – Der Außerirdische ');
    await type('studios', 'NBC, ProSieben');
    await type('userRating', '9');
    element().querySelector<HTMLButtonElement>('.save-button')!.click();

    const request = httpTesting.expectOne({ method: 'PUT', url: `${TVSHOWS_URL}/3/metadata` });
    expect(request.request.body).toEqual({
      ...alf.metadata,
      title: 'ALF – Der Außerirdische',
      studios: ['NBC', 'ProSieben'],
      userRating: 9,
    });
    request.flush(alf);
    await fixture.whenStable();

    expect(snackBarMessages).toEqual(['Gespeichert und in /media/serien/ALF (1986)/tvshow.nfo geschrieben.']);
  });

  it('does not save invalid input and can discard changes', async () => {
    await load(alf);
    await type('tvdbId', 'abc');

    element().querySelector<HTMLButtonElement>('.save-button')!.click();
    httpTesting.expectNone({ method: 'PUT' });

    element().querySelector<HTMLButtonElement>('.reset-button')!.click();
    await fixture.whenStable();
    expect(input('tvdbId').value).toBe('78020');
  });

  it('reports a failed save', async () => {
    await load(alf);
    await type('title', 'Neu');

    element().querySelector<HTMLButtonElement>('.save-button')!.click();
    httpTesting
      .expectOne({ method: 'PUT', url: `${TVSHOWS_URL}/3/metadata` })
      .flush({ detail: 'Keine Schreibrechte' }, { status: 422, statusText: 'Unprocessable' });

    expect(snackBarMessages).toEqual(['Keine Schreibrechte']);
  });

  it('shows an error when the show cannot be loaded', async () => {
    fixture.detectChanges();
    httpTesting.expectOne(`${TVSHOWS_URL}/3`).flush(null, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(element().querySelector('.error')).not.toBeNull();
  });
});
