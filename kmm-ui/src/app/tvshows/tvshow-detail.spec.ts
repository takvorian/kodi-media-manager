import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { ScraperInfo } from '../movies/scraper';
import { NO_THEME, flushWhenRequested } from '../shared/testing';
import { ThemeService } from '../shared/theme';
import { TvShowDetails, TvShowProposal } from './tvshow';
import { TvShowDetail } from './tvshow-detail';
import { TVSHOWS_URL, TV_SHOW_SCRAPERS_URL } from './tvshow.service';
import { TvShowRenamePlan } from './tv-rename';
import { CONFIRM } from '../shared/confirm';

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
      {
        id: 10,
        season: 0,
        episode: 1,
        title: 'Making of',
        aired: null,
        filePath: '/x0.avi',
        playCount: null,
      },
      {
        id: 11,
        season: 1,
        episode: 2,
        title: 'Der Fremde',
        aired: '1986-09-29',
        filePath: '/x1.avi',
        playCount: 1,
      },
      {
        id: 12,
        season: 1,
        episode: 16,
        title: 'Gestatten',
        aired: null,
        filePath: '/x2.avi',
        playCount: null,
      },
    ],
    nfo: {
      path: '/media/serien/ALF (1986)/tvshow.nfo',
      exists: true,
      modifiedAt: '2026-10-26T09:00:00Z',
    },
    trailer: null,
  };

  const scrapers: ScraperInfo[] = [
    {
      id: 'tmdb',
      displayName: 'TMDB',
      mediaTypes: ['TV_SHOW'],
      missingConfiguration: null,
      attribution: null,
    },
    {
      id: 'tvdb',
      displayName: 'TheTVDB',
      mediaTypes: ['TV_SHOW'],
      missingConfiguration: null,
      attribution: { text: 'Metadata provided by TheTVDB.', link: 'https://www.thetvdb.com' },
    },
  ];
  const proposal: TvShowProposal = {
    metadata: {
      ...alf.metadata,
      title: 'ALF',
      plot: 'Ein Außerirdischer bei den Tanners.',
      tmdbId: '4658',
    },
    matchedEpisodes: 2,
    providerEpisodes: 99,
  };

  let fixture: ComponentFixture<TvShowDetail>;
  let httpTesting: HttpTestingController;
  let snackBarMessages: string[];
  let confirmMessages: string[];
  let confirmAnswer: boolean;

  beforeEach(() => {
    snackBarMessages = [];
    confirmMessages = [];
    confirmAnswer = true;
    TestBed.configureTestingModule({
      imports: [TvShowDetail],
      providers: [
        { provide: ThemeService, useValue: { info: () => of(NO_THEME) } },
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
    return Array.from(element().querySelectorAll(selector)).map((item) =>
      item.textContent?.replace(/\s+/g, ' ').trim(),
    );
  }

  async function load(details: TvShowDetails): Promise<void> {
    fixture.detectChanges();
    httpTesting.expectOne({ method: 'GET', url: `${TVSHOWS_URL}/3` }).flush(details);
    httpTesting.expectOne(TV_SHOW_SCRAPERS_URL).flush(scrapers);
    await flushWhenRequested(fixture, httpTesting, `${TVSHOWS_URL}/3/artwork`, []);
    await fixture.whenStable();
  }

  function click(selector: string): void {
    element().querySelector<HTMLButtonElement>(selector)!.click();
  }

  async function adoptFromOnlineSearch(): Promise<void> {
    click('.online-search-button');
    fixture.detectChanges();
    httpTesting.expectOne(TV_SHOW_SCRAPERS_URL).flush(scrapers);
    await fixture.whenStable();
    click('.search-button');
    httpTesting
      .expectOne((request) => request.url === `${TVSHOWS_URL}/3/scraper-search`)
      .flush([
        {
          providerId: '78020',
          title: 'ALF',
          originalTitle: 'ALF',
          year: 1986,
          overview: null,
          posterUrl: null,
        },
      ]);
    await fixture.whenStable();
    click('.adopt-button');
    httpTesting
      .expectOne(
        (request) =>
          request.url === `${TVSHOWS_URL}/3/scraper-proposal` &&
          request.params.get('scraper') === 'tvdb' &&
          request.params.get('providerId') === '78020',
      )
      .flush(proposal);
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

  it('shows a new trailer without discarding unsaved changes', async () => {
    const link = 'plugin://plugin.video.youtube/?action=play_video&videoid=alfTrailer1';
    await load(alf);
    await type('title', 'ALF (neu)');
    expect(element().querySelector('.no-trailer')).not.toBeNull();

    await type('trailerUrl', link);
    click('.apply-link-button');
    httpTesting
      .expectOne({ method: 'PUT', url: `${TVSHOWS_URL}/3/trailer` })
      .flush({ ...alf, trailer: link });
    await fixture.whenStable();

    expect(element().querySelector('.trailer-link')?.textContent).toBe(link);
    expect(input('title').value).toBe('ALF (neu)');
  });

  it('refreshes the show in Kodi on request', async () => {
    await load(alf);

    element().querySelector<HTMLButtonElement>('.kodi-refresh-button')!.click();
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/kodi/tvshows/3/refresh' }).flush([
      {
        hostId: 1,
        host: 'Wohnzimmer',
        outcome: 'REFRESHED',
        message: '„ALF“ aktualisiert.',
        at: '',
      },
    ]);
    element().querySelector<HTMLButtonElement>('.kodi-refresh-button')!.click();
    httpTesting
      .expectOne({ method: 'POST', url: '/api/v1/kodi/tvshows/3/refresh' })
      .flush({ detail: 'Serie nicht gefunden' }, { status: 404, statusText: 'Not Found' });

    expect(snackBarMessages).toEqual(['Wohnzimmer: „ALF“ aktualisiert.', 'Serie nicht gefunden']);
  });

  it('loads the images of a season when it is opened', async () => {
    await load(alf);

    element().querySelectorAll<HTMLElement>('mat-expansion-panel-header')[1].click();
    await flushWhenRequested(fixture, httpTesting, `${TVSHOWS_URL}/3/artwork?season=1`, [
      { type: 'POSTER', exists: false, path: null, modifiedAt: null },
    ]);
    await fixture.whenStable();

    expect(element().querySelector('.season-artwork-title')?.textContent).toContain(
      'Bilder der Staffel',
    );
  });

  it('saves the metadata and reports where the NFO was written', async () => {
    await load({
      ...alf,
      nfo: { ...alf.nfo, exists: false, modifiedAt: null },
      seasons: [],
      episodes: [],
    });
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

    expect(snackBarMessages).toEqual([
      'Gespeichert und in /media/serien/ALF (1986)/tvshow.nfo geschrieben.',
    ]);
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
    httpTesting.expectOne(TV_SHOW_SCRAPERS_URL).flush(scrapers);
    await fixture.whenStable();

    expect(element().querySelector('.error')).not.toBeNull();
  });

  it('shows the TVDB attribution only for shows with a TVDB ID', async () => {
    await load(alf);
    expect(texts('.attribution')).toEqual(['Metadata provided by TheTVDB.']);
    expect(element().querySelector('.attribution a')?.getAttribute('href')).toBe(
      'https://www.thetvdb.com',
    );

    fixture.componentRef.setInput('id', '4');
    fixture.detectChanges();
    httpTesting
      .expectOne(`${TVSHOWS_URL}/4`)
      .flush({ ...alf, id: 4, metadata: { ...alf.metadata, tvdbId: null, tmdbId: '4658' } });
    await flushWhenRequested(fixture, httpTesting, `${TVSHOWS_URL}/4/artwork`, []);
    await fixture.whenStable();
    expect(element().querySelector('.attribution')).toBeNull();
  });

  it('adopts an online search result and fills the episodes after saving', async () => {
    await load(alf);
    await adoptFromOnlineSearch();

    expect(input('tmdbId').value).toBe('4658');
    expect(element().querySelector('app-tvshow-online-search')).toBeNull();
    expect(element().querySelector('.pending-fill')?.textContent).toContain(
      '2 Episoden mit Daten von TheTVDB',
    );
    expect(snackBarMessages).toEqual([
      'Daten von TheTVDB übernommen – bitte prüfen und speichern. Beim Speichern werden 2 Episoden ergänzt.',
    ]);

    click('.save-button');
    httpTesting.expectOne({ method: 'PUT', url: `${TVSHOWS_URL}/3/metadata` }).flush(alf);
    const fill = httpTesting.expectOne(
      (request) => request.url === `${TVSHOWS_URL}/3/scraper-episodes`,
    );
    expect(fill.request.method).toBe('POST');
    expect(fill.request.params.get('providerId')).toBe('78020');
    fill.flush({ updated: 2, unchanged: 0, unmatched: 1, failedFiles: ['/x2.nfo'] });
    fixture.detectChanges();
    httpTesting.expectOne(`${TVSHOWS_URL}/3`).flush(alf);
    httpTesting
      .expectOne({ method: 'POST', url: `/api/v1/tvshows/3/stream-details/after-scrape` })
      .flush({ updated: 4, failures: [] });
    await fixture.whenStable();

    expect(snackBarMessages[1]).toBe(
      'Gespeichert; 2 Episoden mit Daten von TheTVDB ergänzt. 1 NFOs konnten nicht geschrieben werden.',
    );
    expect(snackBarMessages[2]).toBe('Technische Angaben von 4 Episodendateien ausgelesen.');
    expect(element().querySelector('.pending-fill')).toBeNull();
  });

  it('reports episodes that could not be filled and forgets discarded proposals', async () => {
    await load(alf);
    await adoptFromOnlineSearch();
    click('.save-button');
    httpTesting.expectOne({ method: 'PUT', url: `${TVSHOWS_URL}/3/metadata` }).flush(alf);
    httpTesting
      .expectOne((request) => request.url === `${TVSHOWS_URL}/3/scraper-episodes`)
      .flush({ detail: 'TVDB nicht erreichbar' }, { status: 502, statusText: 'Bad Gateway' });
    fixture.detectChanges();
    httpTesting.expectOne(`${TVSHOWS_URL}/3`).flush(alf);
    await fixture.whenStable();
    expect(snackBarMessages[1]).toBe(
      'Serie gespeichert, Episoden nicht ergänzt: TVDB nicht erreichbar',
    );

    await adoptFromOnlineSearch();
    click('.reset-button');
    await fixture.whenStable();
    expect(element().querySelector('.pending-fill')).toBeNull();
  });

  it('lists missing episodes from the preferred scraper', async () => {
    await load(alf);

    click('.missing-button');
    const request = httpTesting.expectOne((req) => req.url === `${TVSHOWS_URL}/3/missing-episodes`);
    expect(request.request.params.get('scraper')).toBe('tvdb');
    request.flush([
      { season: 1, episode: 1, title: 'Hallo, da bin ich!', aired: '1986-09-22' },
      { season: 4, episode: 24, title: null, aired: null },
    ]);
    await fixture.whenStable();

    expect(texts('.missing-title')).toEqual(['Fehlende Episoden (2)']);
    const missing = texts('.missing-episode');
    expect(missing[0]).toMatch(/^S01E01 Hallo, da bin ich! · .*1986$/);
    expect(missing[1]).toBe('S04E24 ohne Titel');
  });

  it('reports when no episodes are missing and when the provider fails', async () => {
    await load(alf);
    click('.missing-button');
    httpTesting
      .expectOne((req) => req.url === `${TVSHOWS_URL}/3/missing-episodes`)
      .flush(
        { detail: 'nicht eindeutig zugeordnet' },
        { status: 422, statusText: 'Unprocessable' },
      );
    await fixture.whenStable();
    expect(snackBarMessages).toEqual(['nicht eindeutig zugeordnet']);

    click('.missing-button');
    httpTesting.expectOne((req) => req.url === `${TVSHOWS_URL}/3/missing-episodes`).flush([]);
    await fixture.whenStable();
    expect(element().querySelector('.no-missing')).not.toBeNull();
  });

  it('needs a scraper for missing episodes', async () => {
    fixture.detectChanges();
    httpTesting.expectOne(`${TVSHOWS_URL}/3`).flush(alf);
    httpTesting.expectOne(TV_SHOW_SCRAPERS_URL).flush([]);
    await flushWhenRequested(fixture, httpTesting, `${TVSHOWS_URL}/3/artwork`, []);
    await fixture.whenStable();

    click('.missing-button');

    expect(snackBarMessages).toEqual(['Es ist kein Serien-Scraper installiert oder aktiv.']);
  });
  describe('renaming', () => {
    const patterns = {
      showFolder: '{show} ({year})',
      seasonFolder: 'Staffel {season}',
      episodeFile: '{show}',
    };
    const plan: TvShowRenamePlan = {
      showId: 3,
      title: 'ALF',
      currentDirectory: '/media/serien/alf',
      targetDirectory: '/media/serien/ALF (1986)',
      files: [
        {
          episodes: 'S01E01',
          currentFolder: '',
          targetFolder: 'Staffel 1',
          renames: [{ currentName: 'a.mkv', targetName: 'ALF - S01E01.mkv' }],
          status: 'RENAME',
          message: null,
        },
        {
          episodes: 'S01E02',
          currentFolder: '',
          targetFolder: 'Staffel 1',
          renames: [],
          status: 'CONFLICT',
          message: 'belegt',
        },
      ],
      status: 'RENAME',
      message: null,
    };

    function answerSettings(): void {
      httpTesting.expectOne({ method: 'GET', url: '/api/v1/settings' }).flush({
        rename: {
          tvShowFolder: patterns.showFolder,
          seasonFolder: patterns.seasonFolder,
          episodeFile: patterns.episodeFile,
        },
      });
    }

    function answerPreview(answer: TvShowRenamePlan): void {
      const preview = httpTesting.expectOne({
        method: 'POST',
        url: `${TVSHOWS_URL}/3/rename/preview`,
      });
      expect(preview.request.body).toEqual(patterns);
      preview.flush(answer);
    }

    it('renames the show after confirming the summary', async () => {
      await load(alf);

      click('.rename-button');
      answerSettings();
      answerPreview(plan);
      httpTesting.expectOne({ method: 'POST', url: `${TVSHOWS_URL}/3/rename` }).flush(plan);
      await flushWhenRequested(fixture, httpTesting, `${TVSHOWS_URL}/3`, alf);
      await fixture.whenStable();

      expect(confirmMessages[0]).toContain('→ /media/serien/ALF (1986)');
      expect(confirmMessages[0]).toContain(
        '1 Episodendateien bekommen neue Namen bzw. Staffelordner.',
      );
      expect(confirmMessages[0]).toContain(
        '1 Episodendateien bleiben wegen Konflikten unverändert.',
      );
      expect(snackBarMessages).toEqual(['„ALF“ umbenannt.']);
    });

    it('keeps everything when the user declines and reports other outcomes', async () => {
      await load(alf);
      confirmAnswer = false;

      click('.rename-button');
      answerSettings();
      answerPreview(plan);
      click('.rename-button');
      answerSettings();
      answerPreview({ ...plan, status: 'UNCHANGED', files: [] });
      click('.rename-button');
      answerSettings();
      answerPreview({ ...plan, status: 'CONFLICT', message: 'Der Ordner existiert bereits.' });

      expect(snackBarMessages).toEqual([
        '„ALF“ heißt schon so.',
        '„ALF“ nicht umbenannt: Der Ordner existiert bereits.',
      ]);
    });

    it('asks to save unsaved changes first and reports problems', async () => {
      await load(alf);
      click('.rename-button');
      answerSettings();
      httpTesting
        .expectOne({ method: 'POST', url: `${TVSHOWS_URL}/3/rename/preview` })
        .flush({ detail: 'kaputt' }, { status: 422, statusText: 'Unprocessable' });

      await type('title', 'ALF 2');
      click('.rename-button');

      expect(snackBarMessages).toEqual(['kaputt', 'Bitte die Änderungen zuerst speichern.']);
    });
  });
});
