import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';

import { MovieDetails } from './movie';
import { MovieDetail } from './movie-detail';
import { MOVIES_URL } from './movie.service';

describe('MovieDetail', () => {
  const heat: MovieDetails = {
    id: 5,
    sourceId: 1,
    filePath: '/media/filme/Heat.mkv',
    discFormat: 'FILE',
    partCount: 1,
    addedAt: '2026-10-25T10:00:00Z',
    metadata: {
      title: 'Heat',
      originalTitle: null,
      sortTitle: null,
      year: 1995,
      premiered: null,
      plot: 'Handlung',
      outline: null,
      tagline: null,
      runtime: 170,
      mpaa: null,
      genres: ['Krimi'],
      imdbId: 'tt0113277',
      tmdbId: null,
    },
    nfo: { path: '/media/filme/Heat.nfo', exists: true, modifiedAt: '2026-10-25T11:00:00Z' },
  };

  let fixture: ComponentFixture<MovieDetail>;
  let httpTesting: HttpTestingController;
  let snackBarMessages: string[];

  beforeEach(() => {
    snackBarMessages = [];
    TestBed.configureTestingModule({
      imports: [MovieDetail],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: MatSnackBar, useValue: { open: (message: string) => snackBarMessages.push(message) } },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MovieDetail);
    fixture.componentRef.setInput('id', '5');
  });

  afterEach(() => httpTesting.verify());

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function input(name: string): HTMLInputElement {
    return element().querySelector<HTMLInputElement>(`[name="${name}"]`)!;
  }

  function button(selector: string): HTMLButtonElement {
    return element().querySelector<HTMLButtonElement>(selector)!;
  }

  async function load(details: MovieDetails): Promise<void> {
    fixture.detectChanges();
    httpTesting.expectOne({ method: 'GET', url: `${MOVIES_URL}/5` }).flush(details);
    // Der Bild-Bereich lädt seine Daten beim Rendern selbst; hier genügt eine leere Liste.
    await renderPendingChanges();
    httpTesting.expectOne({ method: 'GET', url: `${MOVIES_URL}/5/artwork` }).flush([]);
    await fixture.whenStable();
  }

  /** Rendert, bis die gerade beantwortete Anfrage im Template angekommen ist. */
  async function renderPendingChanges(): Promise<void> {
    const renderPasses = 2;
    for (let pass = 0; pass < renderPasses; pass++) {
      await Promise.resolve();
      fixture.detectChanges();
    }
  }

  async function type(name: string, value: string): Promise<void> {
    const field = input(name);
    field.value = value;
    field.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  function expectSave(): TestRequest {
    return httpTesting.expectOne({ method: 'PUT', url: `${MOVIES_URL}/5/metadata` });
  }

  it('shows the movie with its metadata in the form', async () => {
    await load(heat);

    expect(element().querySelector('h2')?.textContent).toBe('Heat');
    expect(element().querySelector('.file-path')?.textContent).toBe('/media/filme/Heat.mkv');
    expect(element().querySelector('.nfo-status')?.textContent).toContain('/media/filme/Heat.nfo');
    expect(input('title').value).toBe('Heat');
    expect(input('genres').value).toBe('Krimi');
    expect(button('.save-button').disabled).toBe(true);
  });

  it('announces where a missing NFO will be created', async () => {
    await load({ ...heat, nfo: { path: '/media/filme/Heat.nfo', exists: false, modifiedAt: null } });

    expect(element().querySelector('.nfo-status')?.textContent).toContain('wird beim Speichern angelegt');
  });

  it('saves changed metadata and shows where the NFO was written', async () => {
    await load(heat);
    await type('title', 'Heat (Director’s Cut)');
    await type('genres', 'Krimi, Drama');

    button('.save-button').click();
    const request = expectSave();
    expect(request.request.body).toMatchObject({ title: 'Heat (Director’s Cut)', genres: ['Krimi', 'Drama'], year: 1995 });
    request.flush({ ...heat, metadata: { ...heat.metadata, title: 'Heat (Director’s Cut)' } });
    await fixture.whenStable();

    expect(snackBarMessages).toEqual(['Gespeichert und in /media/filme/Heat.nfo geschrieben.']);
    expect(element().querySelector('h2')?.textContent).toBe('Heat (Director’s Cut)');
    expect(button('.save-button').disabled).toBe(true);
  });

  it('does not save an invalid form', async () => {
    await load(heat);
    await type('title', '');

    button('.save-button').click();
    await fixture.whenStable();

    httpTesting.expectNone({ method: 'PUT' });
    expect(element().querySelector('mat-error')?.textContent).toContain('erforderlich');
  });

  it('reports a failed save and keeps the changes', async () => {
    await load(heat);
    await type('title', 'Neu');

    button('.save-button').click();
    expectSave().flush(
      { detail: 'Die NFO-Datei kann nicht geschrieben werden. Bitte Schreibrechte im Filmordner prüfen.' },
      { status: 422, statusText: 'Unprocessable' },
    );
    await fixture.whenStable();

    expect(snackBarMessages[0]).toContain('Schreibrechte');
    expect(input('title').value).toBe('Neu');
  });

  it('discards unsaved changes', async () => {
    await load(heat);
    await type('title', 'Neu');

    button('.reset-button').click();
    await fixture.whenStable();

    expect(input('title').value).toBe('Heat');
  });

  it('takes over online data into the form without saving', async () => {
    await load(heat);

    button('.online-search-button').click();
    fixture.detectChanges();
    httpTesting.expectOne('/api/v1/scrapers/metadata').flush([
      { id: 'tmdb', displayName: 'TMDB', mediaTypes: ['MOVIE'], missingConfiguration: null },
    ]);
    await fixture.whenStable();
    button('.search-button').click();
    httpTesting
      .expectOne((request) => request.url === `${MOVIES_URL}/5/scraper-search`)
      .flush([{ providerId: '949', title: 'Heat', originalTitle: 'Heat', year: 1995, overview: null, posterUrl: null }]);
    await fixture.whenStable();
    button('.adopt-button').click();
    httpTesting
      .expectOne((request) => request.url === `${MOVIES_URL}/5/scraper-proposal`)
      .flush({ ...heat.metadata, runtime: 171, mpaa: 'FSK 16', tmdbId: '949' });
    await fixture.whenStable();

    expect(element().querySelector('app-movie-online-search')).toBeNull();
    expect(input('mpaa').value).toBe('FSK 16');
    expect(input('tmdbId').value).toBe('949');
    expect(button('.save-button').disabled).toBe(false);
    expect(snackBarMessages).toEqual(['Daten von TMDB übernommen – bitte prüfen und speichern.']);
    httpTesting.expectNone({ method: 'PUT' });
  });

  it('closes the online search again', async () => {
    await load(heat);

    button('.online-search-button').click();
    fixture.detectChanges();
    httpTesting.expectOne('/api/v1/scrapers/metadata').flush([]);
    button('.online-search-button').click();
    await fixture.whenStable();

    expect(element().querySelector('app-movie-online-search')).toBeNull();
  });

  it('shows an error when the movie cannot be loaded', async () => {
    fixture.detectChanges();
    httpTesting.expectOne(`${MOVIES_URL}/5`).flush({ title: 'Film nicht gefunden' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(element().querySelector('.error')).not.toBeNull();
  });
});
