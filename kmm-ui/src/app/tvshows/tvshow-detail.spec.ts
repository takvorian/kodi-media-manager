import { TestRequest } from '@angular/common/http/testing';
import { By } from '@angular/platform-browser';
import { provideI18nTesting } from '../i18n/testing';
import { LanguageSelect } from '../shared/language-select';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, provideRouter } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { of } from 'rxjs';

import { FakeJobEvents, job } from '../jobs/fake-job-events';

import { ScraperInfo } from '../movies/scraper';
import { NO_THEME, REMOVAL_PLAN, answerDialogs, flushWhenRequested } from '../shared/testing';
import { ThemeService } from '../shared/theme';
import { TvShowDetails, TvShowProposal } from './tvshow';
import { TvShowDetail } from './tvshow-detail';
import { TVSHOWS_URL, TV_SHOW_SCRAPERS_URL } from './tvshow.service';
import { TvShowRenamePlan } from './tv-rename';
import { CONFIRM } from '../shared/confirm';
import { literal } from '../i18n/localized-text';

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
    language: null,
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
      attribution: {
        text: literal('Metadata provided by TheTVDB.'),
        link: 'https://www.thetvdb.com',
      },
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
  let events: FakeJobEvents;

  beforeEach(() => {
    events = new FakeJobEvents();
    snackBarMessages = [];
    confirmMessages = [];
    confirmAnswer = true;
    TestBed.configureTestingModule({
      imports: [TvShowDetail],
      providers: [
        provideI18nTesting(),
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
        answerDialogs(() => ({ deleteFiles: true })),
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
    expect(element().textContent).toContain('TVDB 7,3/10');
    expect(input('genres').value).toBe('Komödie, Familie');
    expect(input('tvdbId').value).toBe('78020');
  });

  it('switches the texts to English at runtime', async () => {
    await load(alf);

    TestBed.inject(TranslocoService).setActiveLang('en');
    await fixture.whenStable();

    expect(texts('.back-link')).toEqual(['← Back to TV shows']);
    expect(texts('.season-title')).toEqual(['Specials', 'Season 1 – Ankunft']);
    expect(texts('mat-panel-description')).toEqual(['1 episode', '2 episodes']);
    expect(texts('.season-refetch-button')).toEqual(['Refetch season', 'Refetch season']);
    expect(texts('.watched')).toEqual(['· watched']);
    expect(element().textContent).toContain('Seasons and episodes');
    expect(element().textContent).toContain('TVDB 7.3/10 (38 votes)');
    expect(element().textContent).toContain('Paul Fusco as ALF');
    expect(element().querySelector('.online-search-button')?.textContent?.trim()).toBe(
      'Search online',
    );
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

  describe('Kodi', () => {
    const refreshing = job({
      id: 'k',
      key: 'kodi-tvshow:3',
      title: literal('„ALF“ in Kodi aktualisieren'),
      done: 1,
      total: 3,
    });

    function kodiButtons(): HTMLButtonElement[] {
      return [
        element().querySelector<HTMLButtonElement>('.kodi-refresh-button')!,
        element().querySelector<HTMLButtonElement>('.kodi-scan-button')!,
        ...element().querySelectorAll<HTMLButtonElement>('.season-kodi-button'),
        ...element().querySelectorAll<HTMLButtonElement>('.season-kodi-scan-button'),
      ];
    }

    it('refreshes the show with its episodes as a job and reports the result of every host', async () => {
      await load(alf);

      element().querySelector<HTMLButtonElement>('.kodi-refresh-button')!.click();
      httpTesting
        .expectOne({ method: 'POST', url: '/api/v1/kodi/tvshows/3/refresh' })
        .flush(refreshing);
      fixture.detectChanges();
      await fixture.whenStable();

      expect(element().querySelector('.kodi-progress')?.textContent).toContain(
        '„ALF“ in Kodi aktualisieren … (1/3)',
      );
      kodiButtons().forEach((button) => expect(button.disabled).toBe(true));

      events.emit({
        ...refreshing,
        status: 'SUCCEEDED',
        done: 3,
        result: [
          {
            hostId: 1,
            host: 'Wohnzimmer',
            outcome: 'REFRESHED',
            message: literal('„ALF“ und 3 Episoden aktualisiert.'),
            at: '',
          },
        ],
      });
      fixture.detectChanges();
      await fixture.whenStable();

      expect(snackBarMessages).toEqual(['Wohnzimmer: „ALF“ und 3 Episoden aktualisiert.']);
      expect(element().querySelector('.kodi-progress')).toBeNull();
      kodiButtons().forEach((button) => expect(button.disabled).toBe(false));
    });

    it('refreshes the episodes of one season', async () => {
      await load(alf);

      element().querySelectorAll<HTMLButtonElement>('.season-kodi-button')[1].click();
      httpTesting
        .expectOne({ method: 'POST', url: '/api/v1/kodi/tvshows/3/seasons/1/refresh' })
        .flush(refreshing);
      events.emit({
        ...refreshing,
        status: 'FAILED',
        message: literal('Kodi antwortet nicht.'),
      });

      expect(snackBarMessages).toEqual([
        '„ALF“ in Kodi aktualisieren fehlgeschlagen: Kodi antwortet nicht.',
      ]);
    });

    it('scans the folder of the show or of a season as a job', async () => {
      await load(alf);

      element().querySelector<HTMLButtonElement>('.kodi-scan-button')!.click();
      httpTesting
        .expectOne({ method: 'POST', url: '/api/v1/kodi/tvshows/3/scan' })
        .flush({ ...refreshing, title: literal('„ALF“ in Kodi scannen'), total: 1, done: 0 });
      events.emit({
        ...refreshing,
        status: 'SUCCEEDED',
        result: [
          {
            hostId: 1,
            host: 'Wohnzimmer',
            outcome: 'SCAN_STARTED',
            message: literal('Kodi sucht nach neuen Episoden.'),
            at: '',
          },
        ],
      });
      fixture.detectChanges();
      await fixture.whenStable();
      element().querySelectorAll<HTMLButtonElement>('.season-kodi-scan-button')[1].click();
      httpTesting
        .expectOne({ method: 'POST', url: '/api/v1/kodi/tvshows/3/seasons/1/scan' })
        .flush(refreshing);

      expect(snackBarMessages).toEqual(['Wohnzimmer: Kodi sucht nach neuen Episoden.']);
    });

    it('reports a refresh that cannot be started', async () => {
      await load(alf);

      element().querySelector<HTMLButtonElement>('.kodi-refresh-button')!.click();
      httpTesting
        .expectOne({ method: 'POST', url: '/api/v1/kodi/tvshows/3/refresh' })
        .flush(
          { detail: 'Kein Kodi-Gerät eingerichtet.' },
          { status: 409, statusText: 'Conflict' },
        );

      expect(snackBarMessages).toEqual(['Kein Kodi-Gerät eingerichtet.']);
    });
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
          message: literal('belegt'),
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
      answerPreview({
        ...plan,
        status: 'CONFLICT',
        message: literal('Der Ordner existiert bereits.'),
      });

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
  describe('scraping', () => {
    const running = job({
      id: 's',
      key: 'tvshow-scrape:3',
      title: literal('Scrapen „ALF“'),
      done: 0,
      total: 1,
    });
    const finished = {
      ...running,
      status: 'SUCCEEDED' as const,
      result: {
        shows: 1,
        episodesUpdated: 0,
        scraped: 1,
        unmatched: 0,
        imagesSaved: 4,
        unmatchedEntries: [],
        failures: [],
        warnings: [literal('fanart.tv: Key fehlt')],
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
      await flushWhenRequested(fixture, httpTesting, `${TVSHOWS_URL}/3`, alf);
      for (let pass = 0; pass < 3; pass++) {
        fixture.detectChanges();
        await Promise.resolve();
        httpTesting.match(`${TVSHOWS_URL}/3/artwork`).forEach((request) => request.flush([]));
      }
      await fixture.whenStable();
      fixture.detectChanges();
    }

    it('starts the job for this entry only', async () => {
      await load(alf);

      scrapeButton().click();

      httpTesting.expectOne({ method: 'POST', url: `${TVSHOWS_URL}/3/auto-scrape` }).flush(running);
    });

    it('asks to save unsaved changes first', async () => {
      await load(alf);
      titleInput().value = 'Geändert';
      titleInput().dispatchEvent(new Event('input'));
      fixture.detectChanges();

      scrapeButton().click();

      expect(snackBarMessages).toEqual(['Bitte die Änderungen zuerst speichern.']);
    });

    it('locks the form and the button while running', async () => {
      await load(alf);

      events.emit(running);
      fixture.detectChanges();
      await fixture.whenStable();

      expect(scrapeButton().disabled).toBe(true);
      expect(titleInput().disabled).toBe(true);
      expect(element().querySelector('.scrape-message')?.textContent).toContain('0 von 1');
    });

    it('reloads and keeps the report open when done', async () => {
      await load(alf);

      await finish();

      expect(titleInput().disabled).toBe(false);
      expect(scrapeButton().disabled).toBe(false);
      expect(element().querySelector('.scrape-summary')?.textContent).toContain(
        'Scrapen „ALF“ abgeschlossen',
      );
      expect(element().querySelector('.warnings li')?.textContent).toContain(
        'fanart.tv: Key fehlt',
      );

      element().querySelector<HTMLButtonElement>('.close-report-button')!.click();
      fixture.detectChanges();
      expect(element().querySelector('.scrape-report')).toBeNull();
    });

    it('ignores the scrape of other entries', async () => {
      await load(alf);

      events.emit({ ...running, id: 'x', key: 'tvshow-scrape:99' });
      events.emit({ ...finished, id: 'x', key: 'tvshow-scrape:99' });
      fixture.detectChanges();

      expect(scrapeButton().disabled).toBe(false);
      expect(element().querySelector('.scrape-report')).toBeNull();
    });

    it('scrapes only one season', async () => {
      await load(alf);

      element().querySelectorAll<HTMLButtonElement>('.season-scrape-button')[1].click();

      httpTesting
        .expectOne({
          method: 'POST',
          url: `${TVSHOWS_URL}/3/seasons/1/auto-scrape?overwrite=false`,
        })
        .flush(running);
      expect(confirmMessages).toEqual([]);
    });

    it('refetches a season only after confirmation', async () => {
      await load(alf);
      const refetch = () =>
        element().querySelectorAll<HTMLButtonElement>('.season-refetch-button')[1].click();

      confirmAnswer = false;
      refetch();
      confirmAnswer = true;
      refetch();

      httpTesting
        .expectOne({
          method: 'POST',
          url: `${TVSHOWS_URL}/3/seasons/1/auto-scrape?overwrite=true`,
        })
        .flush(running);
      expect(confirmMessages.length).toBe(2);
      expect(confirmMessages[0]).toMatch(
        /^Titel und Texte der \d+ Episoden von „Staffel 1 – Ankunft“ werden durch die des Anbieters ersetzt/,
      );
    });

    it('locks the season buttons while the show is scraped', async () => {
      await load(alf);

      events.emit(running);
      fixture.detectChanges();
      await fixture.whenStable();

      const buttons = element().querySelectorAll<HTMLButtonElement>(
        '.season-scrape-button, .season-refetch-button',
      );
      expect(buttons.length).toBe(4);
      buttons.forEach((button) => expect(button.disabled).toBe(true));
    });

    it('asks to save unsaved changes before scraping a season', async () => {
      await load(alf);
      titleInput().value = 'Geändert';
      titleInput().dispatchEvent(new Event('input'));
      fixture.detectChanges();

      element().querySelectorAll<HTMLButtonElement>('.season-refetch-button')[0].click();

      expect(snackBarMessages).toEqual(['Bitte die Änderungen zuerst speichern.']);
      expect(confirmMessages).toEqual([]);
    });

    it('rereads the technical details of one season and reports the result', async () => {
      await load(alf);
      const probing = job({
        id: 'p',
        key: 'stream-details-season:3',
        title: literal('Technische Angaben Staffel 1 von „ALF“'),
        done: 1,
        total: 2,
      });

      element().querySelectorAll<HTMLButtonElement>('.season-streams-button')[1].click();
      httpTesting
        .expectOne({ method: 'POST', url: '/api/v1/tvshows/3/seasons/1/stream-details' })
        .flush(probing);
      fixture.detectChanges();
      await fixture.whenStable();

      expect(element().querySelector('.season-streams-progress')?.textContent).toContain(
        'Technische Angaben Staffel 1 von „ALF“: 1 von 2 Dateien',
      );
      expect(scrapeButton().disabled).toBe(true);
      element()
        .querySelectorAll<HTMLButtonElement>('.season-streams-button')
        .forEach((button) => expect(button.disabled).toBe(true));

      events.emit({
        ...probing,
        status: 'SUCCEEDED',
        done: 2,
        result: { sourceId: 1, updated: 2, skipped: 0, failed: 0, failures: [] },
      });
      fixture.detectChanges();
      await fixture.whenStable();

      expect(snackBarMessages).toEqual([
        'Technische Angaben Staffel 1 von „ALF“ abgeschlossen: 2 ausgelesen.',
      ]);
      expect(scrapeButton().disabled).toBe(false);
      expect(element().querySelector('.season-streams-progress')).toBeNull();
    });

    it('reports a season that cannot be read', async () => {
      await load(alf);

      element().querySelectorAll<HTMLButtonElement>('.season-streams-button')[0].click();
      httpTesting
        .expectOne({ method: 'POST', url: '/api/v1/tvshows/3/seasons/0/stream-details' })
        .flush({ detail: 'Läuft schon.' }, { status: 409, statusText: 'Conflict' });

      expect(snackBarMessages).toEqual(['Läuft schon.']);
    });

    it('reports a scrape that cannot be started', async () => {
      await load(alf);

      scrapeButton().click();
      httpTesting
        .expectOne({ method: 'POST', url: `${TVSHOWS_URL}/3/auto-scrape` })
        .flush({ detail: 'Läuft schon.' }, { status: 409, statusText: 'Conflict' });

      expect(snackBarMessages).toEqual(['Läuft schon.']);
    });
  });

  describe('language', () => {
    function languageSelect(): LanguageSelect {
      return fixture.debugElement.query(By.directive(LanguageSelect)).componentInstance;
    }

    function request(): TestRequest {
      return httpTesting.expectOne({ method: 'POST', url: `${TVSHOWS_URL}/language` });
    }

    it('overwrites the texts when confirmed and reloads after the job', async () => {
      await load(alf);
      expect(languageSelect().value()).toBeNull();

      languageSelect().chosen.emit('fr-FR');

      expect(confirmMessages.at(-1)).toContain('auf Französisch (fr-FR)');
      const change = request();
      expect(change.request.body).toEqual({ showIds: [3], language: 'fr-FR', refetchTexts: true });
      const running = job({ id: 'lang', key: 'tvshows-batch', status: 'RUNNING' });
      change.flush(running);
      events.emit(running);
      fixture.detectChanges();
      expect(element().querySelector('.language-running')).not.toBeNull();
      expect(languageSelect().disabled()).toBe(true);

      events.emit(
        job({
          id: 'lang',
          key: 'tvshows-batch',
          status: 'SUCCEEDED',
          result: { changed: 1, unchanged: 0, failed: 0, failures: [] },
        }),
      );
      await flushWhenRequested(fixture, httpTesting, `${TVSHOWS_URL}/3`, {
        ...alf,
        language: 'fr-FR',
      });
      await fixture.whenStable();
      fixture.detectChanges();

      expect(languageSelect().value()).toBe('fr-FR');
      expect(element().querySelector('.language-running')).toBeNull();
    });

    it('changes only the language when not confirmed', async () => {
      await load(alf);
      confirmAnswer = false;

      languageSelect().chosen.emit(null);

      expect(request().request.body).toEqual({ showIds: [3], language: null, refetchTexts: false });
    });

    it('asks to save unsaved changes first', async () => {
      await load(alf);
      const title = element().querySelector<HTMLInputElement>('[name="title"]')!;
      title.value = 'Geändert';
      title.dispatchEvent(new Event('input'));
      await fixture.whenStable();

      languageSelect().chosen.emit('fr-FR');

      httpTesting.expectNone({ method: 'POST', url: `${TVSHOWS_URL}/language` });
    });

    it('unlocks again when the change is rejected', async () => {
      await load(alf);

      languageSelect().chosen.emit('fr-FR');
      request().flush({ detail: 'Läuft schon' }, { status: 409, statusText: 'Conflict' });
      fixture.detectChanges();

      expect(languageSelect().disabled()).toBe(false);
    });
  });

  it('removes a season after asking and reloads the TV show when the job is done', async () => {
    await load(alf);

    element().querySelectorAll<HTMLButtonElement>('.season-remove-button')[1].click();
    httpTesting
      .expectOne({ method: 'GET', url: `${TVSHOWS_URL}/3/seasons/1/removal` })
      .flush(REMOVAL_PLAN);
    const start = httpTesting.expectOne({
      method: 'POST',
      url: `${TVSHOWS_URL}/3/seasons/1/removal`,
    });
    const removal = job({ id: 'removal', key: 'removal:season:3:1' });
    start.flush(removal);
    events.emit({
      ...removal,
      status: 'SUCCEEDED',
      result: { removedEntries: 2, deletedFiles: 3, failures: [] },
    });
    await flushWhenRequested(fixture, httpTesting, `${TVSHOWS_URL}/3`, alf);
    await fixture.whenStable();

    expect(start.request.body).toEqual({ deleteFiles: true });
    expect(snackBarMessages).toEqual(['2 Einträge entfernt. 3 Dateien gelöscht.']);
  });

  it('removes the TV show after asking and goes back to the list', async () => {
    await load(alf);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    element().querySelector<HTMLButtonElement>('.remove-show-button')!.click();
    httpTesting.expectOne({ method: 'GET', url: `${TVSHOWS_URL}/3/removal` }).flush(REMOVAL_PLAN);
    const start = httpTesting.expectOne({ method: 'POST', url: `${TVSHOWS_URL}/3/removal` });
    start.flush(job({ key: 'removal:tvshow:3' }));

    expect(start.request.body).toEqual({ deleteFiles: true });
    expect(navigate).toHaveBeenCalledWith(['/tvshows']);
  });

  it('names the remove buttons in English', async () => {
    await load(alf);

    TestBed.inject(TranslocoService).setActiveLang('en');
    await fixture.whenStable();

    expect(texts('.remove-show-button')).toEqual(['Remove TV show']);
    expect(texts('.season-remove-button')).toEqual(['Remove season', 'Remove season']);
  });
});
