import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { FakeJobEvents, job } from '../jobs/fake-job-events';

import { EMPTY_SUPPLEMENT, MovieDetails } from './movie';
import { MovieDetail } from './movie-detail';
import { MOVIES_URL } from './movie.service';
import { MovieRenamePlan } from './movie-rename';
import { CollectionReference } from './scraper';
import { CONFIRM } from '../shared/confirm';
import { NO_THEME, flushWhenRequested } from '../shared/testing';
import { ThemeService } from '../shared/theme';

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
      edition: null,
      videoSource: null,
    },
    supplement: { ...EMPTY_SUPPLEMENT, directors: ['Michael Mann'] },
    streams: {
      videos: [
        {
          codec: 'h264',
          aspect: 2.388,
          width: 1280,
          height: 536,
          durationSeconds: 5733,
          bitrate: null,
          scanType: null,
          stereoMode: null,
          language: null,
        },
      ],
      audios: [{ codec: 'dca', language: 'deu', channels: 6, bitrate: null }],
      subtitles: [{ language: 'deu', forced: true }],
    },
    nfo: { path: '/media/filme/Heat.nfo', exists: true, modifiedAt: '2026-10-25T11:00:00Z' },
    trailer: null,
  };

  let fixture: ComponentFixture<MovieDetail>;
  let httpTesting: HttpTestingController;
  let snackBarMessages: string[];
  let confirmMessages: string[];
  let confirmAnswer: boolean;
  let events: FakeJobEvents;

  beforeEach(() => {
    events = new FakeJobEvents();
    snackBarMessages = [];
    confirmMessages = [];
    confirmAnswer = true;
    TestBed.configureTestingModule({
      imports: [MovieDetail],
      providers: [
        { provide: ThemeService, useValue: { info: () => of(NO_THEME) } },
        events.provider(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: MatSnackBar,
          useValue: { open: (message: string) => snackBarMessages.push(message) },
        },
        {
          provide: CONFIRM,
          useValue: (message: string) => {
            confirmMessages.push(message);
            return confirmAnswer;
          },
        },
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
    expect(element().querySelector('.videos')?.textContent?.trim()).toBe(
      'H.264 · 1280×536 · 2.39:1 · 1 h 36 min',
    );
    expect(element().querySelector('.audios')?.textContent?.trim()).toBe('deu · DTS · 5.1');
    expect(element().querySelector('.subtitles')?.textContent?.trim()).toBe('deu (erzwungen)');
  });

  it('refreshes the movie in Kodi and reports each host', async () => {
    await load(heat);

    button('.kodi-refresh-button').click();
    fixture.detectChanges();
    expect(button('.kodi-refresh-button').disabled).toBe(true);
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/kodi/movies/5/refresh' }).flush([
      {
        hostId: 1,
        host: 'Wohnzimmer',
        outcome: 'REFRESHED',
        message: '„Heat“ aktualisiert.',
        at: '',
      },
      { hostId: 2, host: 'Küche', outcome: 'FAILED', message: 'nicht erreichbar.', at: '' },
    ]);
    fixture.detectChanges();
    button('.kodi-refresh-button').click();
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/kodi/movies/5/refresh' }).flush([]);
    fixture.detectChanges();
    button('.kodi-refresh-button').click();
    httpTesting
      .expectOne({ method: 'POST', url: '/api/v1/kodi/movies/5/refresh' })
      .flush({ detail: 'Film nicht gefunden' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(snackBarMessages).toEqual([
      'Wohnzimmer: „Heat“ aktualisiert. Küche: nicht erreichbar.',
      'Kein Kodi-Gerät eingerichtet – unter „Kodi“ anlegen.',
      'Film nicht gefunden',
    ]);
    expect(button('.kodi-refresh-button').disabled).toBe(false);
  });

  it('imports the watched status from Kodi without discarding unsaved changes', async () => {
    await load(heat);
    await type('title', 'Heat (Director’s Cut)');

    button('.kodi-playback-button').click();
    fixture.detectChanges();
    expect(button('.kodi-playback-button').disabled).toBe(true);
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/kodi/movies/5/playback' }).flush({
      host: 'Wohnzimmer',
      outcome: 'UPDATED',
      message: '„Heat“ aus Kodi übernommen: 2× gesehen.',
      playCount: 2,
      lastPlayedAt: '2025-04-03T21:14:16Z',
      userRating: 10,
    });
    await fixture.whenStable();

    expect(element().querySelector('app-movie-supplement')?.textContent).toContain('2×');
    expect(element().querySelector('app-movie-supplement')?.textContent).toContain('10/10');
    expect(input('title').value).toBe('Heat (Director’s Cut)');
    button('.kodi-playback-button').click();
    httpTesting
      .expectOne({ method: 'POST', url: '/api/v1/kodi/movies/5/playback' })
      .flush(
        { detail: 'Kein Kodi-Gerät hat eine Pfad-Zuordnung.' },
        { status: 422, statusText: 'Unprocessable' },
      );
    fixture.detectChanges();

    expect(snackBarMessages).toEqual([
      'Wohnzimmer: „Heat“ aus Kodi übernommen: 2× gesehen.',
      'Kein Kodi-Gerät hat eine Pfad-Zuordnung.',
    ]);
    expect(button('.kodi-playback-button').disabled).toBe(false);
  });

  it('shows a new trailer without discarding unsaved changes', async () => {
    const link = 'plugin://plugin.video.youtube/?action=play_video&videoid=2GfZl4kuVNI';
    await load(heat);
    await type('title', 'Heat (Director’s Cut)');
    expect(element().querySelector('.no-trailer')).not.toBeNull();

    await type('trailerUrl', link);
    button('.apply-link-button').click();
    httpTesting
      .expectOne({ method: 'PUT', url: `${MOVIES_URL}/5/trailer` })
      .flush({ ...heat, trailer: link });
    await fixture.whenStable();

    expect(element().querySelector('.trailer-link')?.textContent).toBe(link);
    expect(input('title').value).toBe('Heat (Director’s Cut)');
  });

  it('reads the technical details with ffprobe', async () => {
    await load(heat);

    button('.stream-details-button').click();
    fixture.detectChanges();
    expect(button('.stream-details-button').disabled).toBe(true);
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/movies/5/stream-details' }).flush({
      videos: [],
      audios: [{ codec: 'truehd', language: 'deu', channels: 8, bitrate: null }],
      subtitles: [],
    });
    await fixture.whenStable();

    expect(element().querySelector('app-stream-details')?.textContent).toContain('Dolby TrueHD');
    button('.stream-details-button').click();
    httpTesting
      .expectOne({ method: 'POST', url: '/api/v1/movies/5/stream-details' })
      .flush(
        { detail: 'ffprobe kann nicht gestartet werden' },
        { status: 422, statusText: 'Unprocessable' },
      );
    fixture.detectChanges();

    expect(snackBarMessages).toEqual([
      'Technische Angaben ausgelesen.',
      'ffprobe kann nicht gestartet werden',
    ]);
    expect(button('.stream-details-button').disabled).toBe(false);
  });

  it('announces where a missing NFO will be created', async () => {
    await load({
      ...heat,
      nfo: { path: '/media/filme/Heat.nfo', exists: false, modifiedAt: null },
    });

    expect(element().querySelector('.nfo-status')?.textContent).toContain(
      'wird beim Speichern angelegt',
    );
  });

  it('saves changed metadata and shows where the NFO was written', async () => {
    await load(heat);
    await type('title', 'Heat (Director’s Cut)');
    await type('genres', 'Krimi, Drama');

    button('.save-button').click();
    const request = expectSave();
    expect(request.request.body).toMatchObject({
      title: 'Heat (Director’s Cut)',
      genres: ['Krimi', 'Drama'],
      year: 1995,
    });
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
      {
        detail:
          'Die NFO-Datei kann nicht geschrieben werden. Bitte Schreibrechte im Filmordner prüfen.',
      },
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
    httpTesting
      .expectOne('/api/v1/scrapers/metadata')
      .flush([
        { id: 'tmdb', displayName: 'TMDB', mediaTypes: ['MOVIE'], missingConfiguration: null },
      ]);
    await fixture.whenStable();
    button('.search-button').click();
    httpTesting
      .expectOne((request) => request.url === `${MOVIES_URL}/5/scraper-search`)
      .flush([
        {
          providerId: '949',
          title: 'Heat',
          originalTitle: 'Heat',
          year: 1995,
          overview: null,
          posterUrl: null,
        },
      ]);
    await fixture.whenStable();
    button('.adopt-button').click();
    httpTesting
      .expectOne((request) => request.url === `${MOVIES_URL}/5/scraper-proposal`)
      .flush({
        metadata: { ...heat.metadata, runtime: 171, mpaa: 'FSK 16', tmdbId: '949' },
        collection: null,
      });
    await fixture.whenStable();

    expect(element().querySelector('app-movie-online-search')).toBeNull();
    expect(input('mpaa').value).toBe('FSK 16');
    expect(input('tmdbId').value).toBe('949');
    expect(button('.save-button').disabled).toBe(false);
    expect(snackBarMessages).toEqual(['Daten von TMDB übernommen – bitte prüfen und speichern.']);
    httpTesting.expectNone({ method: 'PUT' });
  });

  it('assigns the movie to the set of its collection after saving online data, then reads the details', async () => {
    await load(heat);
    await adoptFromOnlineSearch({ providerId: '1', name: 'Heat-Reihe' });

    button('.save-button').click();
    expectSave().flush(heat);
    const assign = httpTesting.expectOne({ method: 'POST', url: `${MOVIES_URL}/5/collection` });
    expect(assign.request.body).toEqual({ providerId: '1', name: 'Heat-Reihe' });
    httpTesting.expectNone(`${MOVIES_URL}/5/stream-details/after-scrape`);
    assign.flush({ assigned: true });
    httpTesting
      .expectOne({ method: 'POST', url: `${MOVIES_URL}/5/stream-details/after-scrape` })
      .flush(null);
    await fixture.whenStable();

    expect(snackBarMessages).toContain('Dem Filmset „Heat-Reihe“ zugeordnet.');
  });

  it('reads the technical details after saving online data', async () => {
    await load(heat);
    await adoptFromOnlineSearch();

    button('.save-button').click();
    expectSave().flush(heat);
    httpTesting
      .expectOne({ method: 'POST', url: `${MOVIES_URL}/5/stream-details/after-scrape` })
      .flush({
        videos: [],
        audios: [{ codec: 'truehd', language: 'deu', channels: 8, bitrate: null }],
        subtitles: [],
      });
    await fixture.whenStable();

    expect(element().querySelector('app-stream-details')?.textContent).toContain('Dolby TrueHD');
    expect(snackBarMessages).toContain('Gespeichert; technische Angaben ausgelesen.');

    await type('title', 'Heat 2');
    button('.save-button').click();
    expectSave().flush(heat);
    await fixture.whenStable();
    httpTesting.expectNone(`${MOVIES_URL}/5/stream-details/after-scrape`);
  });

  it('reports technical details that cannot be read after saving online data', async () => {
    await load(heat);
    await adoptFromOnlineSearch();

    button('.save-button').click();
    expectSave().flush(heat);
    httpTesting
      .expectOne(`${MOVIES_URL}/5/stream-details/after-scrape`)
      .flush({ detail: 'ffprobe fehlt' }, { status: 422, statusText: 'Unprocessable' });
    await fixture.whenStable();

    expect(snackBarMessages).toContain(
      'Gespeichert, technische Angaben nicht ausgelesen: ffprobe fehlt',
    );
    expect(button('.stream-details-button').disabled).toBe(false);
  });

  it('does not read the technical details after discarding online data', async () => {
    await load(heat);
    await adoptFromOnlineSearch();
    button('.reset-button').click();
    await type('title', 'Heat 2');

    button('.save-button').click();
    expectSave().flush(heat);
    await fixture.whenStable();

    httpTesting.expectNone(`${MOVIES_URL}/5/stream-details/after-scrape`);
  });

  async function adoptFromOnlineSearch(
    collection: CollectionReference | null = null,
  ): Promise<void> {
    button('.online-search-button').click();
    fixture.detectChanges();
    httpTesting
      .expectOne('/api/v1/scrapers/metadata')
      .flush([
        { id: 'tmdb', displayName: 'TMDB', mediaTypes: ['MOVIE'], missingConfiguration: null },
      ]);
    await fixture.whenStable();
    button('.search-button').click();
    httpTesting
      .expectOne((request) => request.url === `${MOVIES_URL}/5/scraper-search`)
      .flush([
        {
          providerId: '949',
          title: 'Heat',
          originalTitle: 'Heat',
          year: 1995,
          overview: null,
          posterUrl: null,
        },
      ]);
    await fixture.whenStable();
    button('.adopt-button').click();
    httpTesting
      .expectOne((request) => request.url === `${MOVIES_URL}/5/scraper-proposal`)
      .flush({ metadata: { ...heat.metadata, tmdbId: '949' }, collection });
    await fixture.whenStable();
  }

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
    httpTesting
      .expectOne(`${MOVIES_URL}/5`)
      .flush({ title: 'Film nicht gefunden' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(element().querySelector('.error')).not.toBeNull();
  });
  describe('renaming', () => {
    const patterns = { folder: '{title} ({year})', file: '{title}' };
    const plan: MovieRenamePlan = {
      movieId: 5,
      title: 'Heat',
      currentDirectory: '/media/filme/heat',
      targetDirectory: '/media/filme/Heat (1995)',
      renames: [{ currentName: 'heat.mkv', targetName: 'Heat.mkv' }],
      status: 'RENAME',
      message: null,
    };

    function answerSettings(): void {
      httpTesting
        .expectOne({ method: 'GET', url: '/api/v1/settings' })
        .flush({ rename: { movieFolder: patterns.folder, movieFile: patterns.file } });
    }

    function answerPreview(answer: MovieRenamePlan): void {
      const preview = httpTesting.expectOne({
        method: 'POST',
        url: `${MOVIES_URL}/5/rename/preview`,
      });
      expect(preview.request.body).toEqual(patterns);
      preview.flush(answer);
    }

    it('renames the movie after confirming the new names', async () => {
      await load(heat);

      button('.rename-button').click();
      answerSettings();
      answerPreview(plan);
      httpTesting.expectOne({ method: 'POST', url: `${MOVIES_URL}/5/rename` }).flush(plan);
      await renderPendingChanges();
      httpTesting.expectOne({ method: 'GET', url: `${MOVIES_URL}/5` }).flush(heat);
      await renderPendingChanges();
      httpTesting.match(`${MOVIES_URL}/5/artwork`).forEach((request) => request.flush([]));

      expect(confirmMessages[0]).toContain('→ /media/filme/Heat (1995)');
      expect(confirmMessages[0]).toContain('heat.mkv → Heat.mkv');
      expect(snackBarMessages).toEqual(['„Heat“ umbenannt.']);
    });

    it('keeps everything when the user declines', async () => {
      await load(heat);
      confirmAnswer = false;

      button('.rename-button').click();
      answerSettings();
      answerPreview(plan);

      expect(snackBarMessages).toEqual([]);
      expect(button('.rename-button').disabled).toBe(false);
    });

    it('reports names that are right already and conflicts', async () => {
      await load(heat);

      button('.rename-button').click();
      answerSettings();
      answerPreview({ ...plan, status: 'UNCHANGED', renames: [] });
      button('.rename-button').click();
      answerSettings();
      answerPreview({ ...plan, status: 'CONFLICT', message: 'Der Ordner existiert bereits.' });

      expect(confirmMessages).toEqual([]);
      expect(snackBarMessages).toEqual([
        '„Heat“ heißt schon so.',
        '„Heat“ nicht umbenannt: Der Ordner existiert bereits.',
      ]);
    });

    it('asks to save unsaved changes first', async () => {
      await load(heat);
      await type('title', 'Heat 2');

      button('.rename-button').click();

      expect(snackBarMessages).toEqual(['Bitte die Änderungen zuerst speichern.']);
    });

    it('reports problems', async () => {
      await load(heat);

      button('.rename-button').click();
      answerSettings();
      httpTesting
        .expectOne({ method: 'POST', url: `${MOVIES_URL}/5/rename/preview` })
        .flush({ detail: 'Ordnermuster: kaputt' }, { status: 422, statusText: 'Unprocessable' });

      expect(snackBarMessages).toEqual(['Ordnermuster: kaputt']);
    });
  });
  describe('scraping', () => {
    const running = job({
      id: 's',
      key: 'movie-scrape:5',
      title: 'Scrapen „Heat“',
      done: 0,
      total: 1,
    });
    const finished = {
      ...running,
      status: 'SUCCEEDED' as const,
      result: {
        movies: 1,
        scraped: 1,
        unmatched: 0,
        imagesSaved: 4,
        unmatchedEntries: [],
        failures: [],
        warnings: ['fanart.tv: Key fehlt'],
      },
    };

    function scrapeButton(): HTMLButtonElement {
      return element().querySelector<HTMLButtonElement>('.scrape-button')!;
    }

    function titleInput(): HTMLInputElement {
      return element().querySelector<HTMLInputElement>('[name="title"]')!;
    }

    async function finish(): Promise<void> {
      events.emit(running);
      fixture.detectChanges();
      await fixture.whenStable();
      events.emit(finished);
      await flushWhenRequested(fixture, httpTesting, `${MOVIES_URL}/5`, heat);
      for (let pass = 0; pass < 3; pass++) {
        fixture.detectChanges();
        await Promise.resolve();
        httpTesting.match(`${MOVIES_URL}/5/artwork`).forEach((request) => request.flush([]));
      }
      await fixture.whenStable();
      fixture.detectChanges();
    }

    it('starts the job for this entry only', async () => {
      await load(heat);

      scrapeButton().click();

      httpTesting.expectOne({ method: 'POST', url: `${MOVIES_URL}/5/auto-scrape` }).flush(running);
    });

    it('asks to save unsaved changes first', async () => {
      await load(heat);
      titleInput().value = 'Geändert';
      titleInput().dispatchEvent(new Event('input'));
      fixture.detectChanges();

      scrapeButton().click();

      expect(snackBarMessages).toEqual(['Bitte die Änderungen zuerst speichern.']);
    });

    it('locks the form and the button while running', async () => {
      await load(heat);

      events.emit(running);
      fixture.detectChanges();
      await fixture.whenStable();

      expect(scrapeButton().disabled).toBe(true);
      expect(titleInput().disabled).toBe(true);
      expect(element().querySelector('.scrape-message')?.textContent).toContain('0 von 1');
    });

    it('reloads and keeps the report open when done', async () => {
      await load(heat);

      await finish();

      expect(titleInput().disabled).toBe(false);
      expect(scrapeButton().disabled).toBe(false);
      expect(element().querySelector('.scrape-summary')?.textContent).toContain(
        'Scrapen „Heat“ abgeschlossen',
      );
      expect(element().querySelector('.warnings li')?.textContent).toContain(
        'fanart.tv: Key fehlt',
      );

      element().querySelector<HTMLButtonElement>('.close-report-button')!.click();
      fixture.detectChanges();
      expect(element().querySelector('.scrape-report')).toBeNull();
    });

    it('ignores the scrape of other entries', async () => {
      await load(heat);

      events.emit({ ...running, id: 'x', key: 'movie-scrape:99' });
      events.emit({ ...finished, id: 'x', key: 'movie-scrape:99' });
      fixture.detectChanges();

      expect(scrapeButton().disabled).toBe(false);
      expect(element().querySelector('.scrape-report')).toBeNull();
    });

    it('reports a scrape that cannot be started', async () => {
      await load(heat);

      scrapeButton().click();
      httpTesting
        .expectOne({ method: 'POST', url: `${MOVIES_URL}/5/auto-scrape` })
        .flush({ detail: 'Läuft schon.' }, { status: 409, statusText: 'Conflict' });

      expect(snackBarMessages).toEqual(['Läuft schon.']);
    });
  });
});
