import { flushWhenRequested } from '../shared/testing';
import { By } from '@angular/platform-browser';
import { LanguageSelect } from '../shared/language-select';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';

import { LanguageService } from '../i18n/language.service';
import { provideI18nTesting } from '../i18n/testing';
import { FakeJobEvents, job } from '../jobs/fake-job-events';
import { Job } from '../jobs/job';
import { CONFIRM } from '../shared/confirm';
import { AutoScrapeResult, MediaSource } from './media-source';
import { SOURCES_URL } from './media-source.service';
import { SourceList } from './source-list';
import { literal } from '../i18n/localized-text';

describe('SourceList', () => {
  const films: MediaSource = {
    id: 1,
    name: 'Filme',
    path: '/media/filme',
    mediaType: 'MOVIE',
    createdAt: '2026-10-25T10:00:00Z',
    lastScannedAt: null,
    metadataLanguage: null,
  };
  const series: MediaSource = {
    ...films,
    id: 2,
    name: 'Serien',
    path: '/media/serien',
    mediaType: 'TV_SHOW',
  };

  let fixture: ComponentFixture<SourceList>;
  let httpTesting: HttpTestingController;
  let snackBarMessages: string[];
  let confirmAnswer: boolean;
  let events: FakeJobEvents;

  beforeEach(() => {
    snackBarMessages = [];
    confirmAnswer = true;
    events = new FakeJobEvents();
    TestBed.configureTestingModule({
      imports: [SourceList],
      providers: [
        provideI18nTesting(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: MatSnackBar,
          useValue: { open: (message: string) => snackBarMessages.push(message) },
        },
        { provide: CONFIRM, useValue: () => confirmAnswer },
        events.provider(),
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SourceList);
  });

  afterEach(() => httpTesting.verify());

  async function loadSources(sources: MediaSource[]): Promise<void> {
    fixture.detectChanges();
    await answerReload(sources);
  }

  /** Answers the (re)load of the list that a resource reload triggers on the next rendering. */
  async function answerReload(sources: MediaSource[]): Promise<void> {
    fixture.detectChanges();
    expectList().flush(sources);
    await fixture.whenStable();
  }

  function expectList(): TestRequest {
    return httpTesting.expectOne({ method: 'GET', url: SOURCES_URL });
  }

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function button(selector: string, row = 0): HTMLButtonElement {
    return element().querySelectorAll<HTMLButtonElement>(selector)[row];
  }

  /**
   * Switches like the language menu does. Without `init()` the service still holds its fallback language, so it is
   * first set to the German of the test setup; otherwise the switch to English would not be a change.
   */
  async function switchToEnglish(): Promise<void> {
    const language = TestBed.inject(LanguageService);
    await language.use('de');
    await language.use('en');
  }

  async function fillForm(name: string, path: string): Promise<void> {
    for (const [field, value] of [
      ['name', name],
      ['path', path],
    ]) {
      const input = element().querySelector<HTMLInputElement>(`input[name="${field}"]`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    await fixture.whenStable();
  }

  it('lists the sources', async () => {
    await loadSources([films, series]);

    expect(
      Array.from(element().querySelectorAll('.source-name')).map((cell) => cell.textContent),
    ).toEqual(['Filme', 'Serien']);
  });

  it('switches the texts to English at runtime', async () => {
    await loadSources([films, series]);

    await switchToEnglish();
    await fixture.whenStable();

    expect(element().querySelector('.page-title')?.textContent).toBe('Sources');
    expect(element().querySelector('.scan-button')?.textContent?.trim()).toBe('Scan');
    expect(
      Array.from(element().querySelectorAll('td.mat-column-mediaType')).map((cell) =>
        cell.textContent?.trim(),
      ),
    ).toEqual(['Movies', 'TV shows']);
  });

  it('offers scanning for movie and TV sources but scraping only for movies', async () => {
    await loadSources([films, series, { ...films, id: 3, name: 'Sets', mediaType: 'MOVIE_SET' }]);

    expect(element().querySelectorAll('.scan-button')).toHaveLength(2);
    expect(element().querySelectorAll('.scrape-button')).toHaveLength(2);
    const renameLinks = element().querySelectorAll<HTMLAnchorElement>('.rename-link');
    expect([...renameLinks].map((link) => link.getAttribute('href'))).toEqual([
      '/sources/1/rename',
      '/sources/2/tv-rename',
    ]);
  });

  it('reports episodes without recognizable number after a TV scan', async () => {
    await loadSources([series]);
    events.emit(job({ key: 'scan:2', title: literal('Scan „Serien“') }));

    events.emit(
      job({
        key: 'scan:2',
        title: literal('Scan „Serien“'),
        status: 'SUCCEEDED',
        result: {
          sourceId: 2,
          added: 4,
          updated: 0,
          removed: 0,
          unchanged: 0,
          nfoErrors: 0,
          unrecognizedFiles: ['x.avi'],
        },
      }),
    );
    await answerReload([series]);

    expect(snackBarMessages).toEqual([
      'Scan „Serien“ abgeschlossen: 4 neu, 0 aktualisiert, 0 entfernt, 0 unverändert, 1 Datei ohne erkennbare Episode.',
    ]);
  });

  it('lets Kodi scan movie and tv sources', async () => {
    await loadSources([films, series]);
    expect(element().querySelectorAll('.kodi-scan-button')).toHaveLength(2);

    element().querySelector<HTMLButtonElement>('.kodi-scan-button')!.click();
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/kodi/sources/1/scan' }).flush([
      {
        hostId: 1,
        host: 'Wohnzimmer',
        outcome: 'SCAN_STARTED',
        message: literal('Kodi sucht nach neuen Filmen.'),
        at: '',
      },
    ]);
    element().querySelector<HTMLButtonElement>('.kodi-scan-button')!.click();
    httpTesting
      .expectOne({ method: 'POST', url: '/api/v1/kodi/sources/1/scan' })
      .flush({ detail: 'Quelle nicht gefunden' }, { status: 404, statusText: 'Not Found' });

    expect(snackBarMessages).toEqual([
      'Wohnzimmer: Kodi sucht nach neuen Filmen.',
      'Quelle nicht gefunden',
    ]);
  });

  it('imports the watched status of a source from Kodi as job', async () => {
    await loadSources([films, series]);
    const playbackJob = job({
      id: 'job-7',
      key: 'kodi-playback:1',
      title: literal('Gesehen-Status „Filme“ aus Kodi'),
    });

    button('.kodi-playback-button').click();
    httpTesting
      .expectOne({ method: 'POST', url: '/api/v1/kodi/sources/1/playback' })
      .flush(playbackJob);
    fixture.detectChanges();
    expect(element().querySelector('.job-progress')).not.toBeNull();
    events.emit({
      ...playbackJob,
      status: 'SUCCEEDED',
      result: {
        sourceId: 1,
        host: 'Wohnzimmer',
        updated: 1,
        unchanged: 2,
        kept: 0,
        notInKodi: 0,
        failed: 0,
        failures: [],
      },
    });
    fixture.detectChanges();

    expect(snackBarMessages).toEqual([
      'Gesehen-Status „Filme“ aus Kodi (Wohnzimmer) abgeschlossen: 1 übernommen, 2 unverändert, 0 in Kodi unbekannt.',
    ]);
  });

  it('reads the technical details of a source as job', async () => {
    await loadSources([films, series]);
    const probeJob = job({
      id: 'job-8',
      key: 'stream-details:1',
      title: literal('Technische Angaben „Filme“'),
    });

    button('.stream-details-button').click();
    fixture.detectChanges();
    document.querySelector<HTMLButtonElement>('.stream-details-all')!.click();
    httpTesting
      .expectOne({ method: 'POST', url: '/api/v1/sources/1/stream-details?scope=ALL' })
      .flush(probeJob);
    fixture.detectChanges();
    expect(element().querySelector('.job-progress')).not.toBeNull();
    events.emit({
      ...probeJob,
      status: 'SUCCEEDED',
      result: { sourceId: 1, updated: 3, skipped: 0, failed: 0, failures: [] },
    });
    fixture.detectChanges();
    button('.stream-details-button').click();
    fixture.detectChanges();
    document.querySelector<HTMLButtonElement>('.stream-details-missing')!.click();
    httpTesting
      .expectOne({ method: 'POST', url: '/api/v1/sources/1/stream-details?scope=MISSING' })
      .flush({ detail: 'ffprobe fehlt' }, { status: 409, statusText: 'Conflict' });

    expect(snackBarMessages).toEqual([
      'Technische Angaben „Filme“ abgeschlossen: 3 ausgelesen.',
      'ffprobe fehlt',
    ]);
  });

  it('shows a hint without sources', async () => {
    await loadSources([]);

    expect(element().querySelector('.empty')?.textContent).toContain('Noch keine Quellen');
  });

  it('shows an error when the sources cannot be loaded', async () => {
    fixture.detectChanges();
    expectList().flush('boom', { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(element().querySelector('.error')).not.toBeNull();
  });

  it('creates a source and reloads the list', async () => {
    await loadSources([]);
    await fillForm('Filme', '/media/filme');

    button('.create-button').click();
    const request = httpTesting.expectOne({ method: 'POST', url: SOURCES_URL });
    expect(request.request.body).toEqual({
      name: 'Filme',
      path: '/media/filme',
      mediaType: 'MOVIE',
      metadataLanguage: null,
    });
    request.flush(films);
    await answerReload([films]);

    expect(snackBarMessages).toEqual(['Quelle „Filme“ angelegt.']);
    expect(element().querySelector('.source-name')?.textContent).toBe('Filme');
  });

  it('keeps the create button disabled while the form is incomplete', async () => {
    await loadSources([]);
    await fillForm('Filme', '');

    expect(button('.create-button').disabled).toBe(true);
  });

  it('creates a source with its language and forgets the choice afterwards', async () => {
    await loadSources([]);
    await fillForm('Filme', '/media/filme');
    const select = () => fixture.debugElement.query(By.directive(LanguageSelect)).componentInstance;

    select().chosen.emit('en-US');
    fixture.detectChanges();
    expect(select().value()).toBe('en-US');
    button('.create-button').click();

    const request = httpTesting.expectOne({ method: 'POST', url: SOURCES_URL });
    expect(request.request.body.metadataLanguage).toBe('en-US');
    request.flush(films);
    await flushWhenRequested(fixture, httpTesting, SOURCES_URL, []);
    fixture.detectChanges();
    expect(select().value()).toBeNull();
  });

  it('changes the language of a source and reloads the list', async () => {
    await loadSources([films]);
    const selects = fixture.debugElement.queryAll(By.directive(LanguageSelect));

    selects.at(-1)!.componentInstance.chosen.emit('fr-FR');

    const request = httpTesting.expectOne({
      method: 'PUT',
      url: `${SOURCES_URL}/${films.id}/language`,
    });
    expect(request.request.body).toEqual({ language: 'fr-FR' });
    request.flush({ ...films, metadataLanguage: 'fr-FR' });
    await flushWhenRequested(fixture, httpTesting, SOURCES_URL, [
      { ...films, metadataLanguage: 'fr-FR' },
    ]);
    fixture.detectChanges();

    const reloaded = fixture.debugElement.queryAll(By.directive(LanguageSelect));
    expect(reloaded.at(-1)!.componentInstance.value()).toBe('fr-FR');
  });

  it('shows the problem detail when changing the language fails', async () => {
    await loadSources([films]);

    fixture.debugElement
      .queryAll(By.directive(LanguageSelect))
      .at(-1)!
      .componentInstance.chosen.emit('fr-FR');
    httpTesting
      .expectOne({ method: 'PUT', url: `${SOURCES_URL}/${films.id}/language` })
      .flush({ detail: 'Nein' }, { status: 400, statusText: 'Bad Request' });

    expect(snackBarMessages).toContain('Nein');
  });

  it('shows the problem detail when creating fails', async () => {
    await loadSources([]);
    await fillForm('Filme', '/fehlt');

    button('.create-button').click();
    httpTesting
      .expectOne({ method: 'POST', url: SOURCES_URL })
      .flush(
        { detail: "'/fehlt' ist kein erreichbares Verzeichnis." },
        { status: 422, statusText: 'Unprocessable' },
      );
    await fixture.whenStable();

    expect(snackBarMessages).toEqual(["'/fehlt' ist kein erreichbares Verzeichnis."]);
    expect(button('.create-button').disabled).toBe(false);
  });

  it('starts a scan and shows its progress instead of the scan button', async () => {
    await loadSources([films]);

    button('.scan-button').click();
    httpTesting.expectOne({ method: 'POST', url: `${SOURCES_URL}/1/scan` }).flush(job());
    events.emit(job({ done: 1, total: 4, message: literal('NFOs werden geprüft') }));
    fixture.detectChanges();

    expect(element().querySelector('.scan-button')).toBeNull();
    expect(element().querySelector('.job-message')?.textContent).toContain(
      'NFOs werden geprüft (25 %)',
    );
    expect(button('.delete-button').disabled).toBe(true);
  });

  it('reports the result when the scan finishes and reloads the sources', async () => {
    await loadSources([films]);
    events.emit(job());

    events.emit(
      job({
        status: 'SUCCEEDED',
        result: { sourceId: 1, added: 3, updated: 2, removed: 1, unchanged: 7, nfoErrors: 1 },
      }),
    );
    await answerReload([
      { ...films, lastScannedAt: '2026-10-25T12:00:00Z', metadataLanguage: null },
    ]);

    expect(snackBarMessages).toEqual([
      'Scan „Filme“ abgeschlossen: 3 neu, 2 aktualisiert, 1 entfernt, 7 unverändert, 1 NFO-Fehler.',
    ]);
    expect(element().querySelector('.scan-button')).not.toBeNull();
  });

  it('sorts by a column header', async () => {
    await loadSources([series, films]);
    const names = () =>
      Array.from(element().querySelectorAll('.source-name')).map((cell) => cell.textContent);
    expect(names()).toEqual(['Filme', 'Serien']);

    element().querySelector<HTMLElement>('th.mat-column-name .mat-sort-header-container')!.click();
    await fixture.whenStable();

    expect(names()).toEqual(['Serien', 'Filme']);
  });

  it('shows the time of the last scan in the language of the browser', async () => {
    const lastScannedAt = '2026-10-25T12:30:00Z';
    await loadSources([films, { ...series, lastScannedAt }]);

    const cells = element().querySelectorAll('.last-scanned-at');
    expect(cells[0].textContent).toBe('–');
    expect(cells[1].textContent).toBe(
      new Date(lastScannedAt).toLocaleString(undefined, {
        dateStyle: 'medium',
        timeStyle: 'short',
      }),
    );
  });

  describe('report of the last scan', () => {
    const scanJob = job({
      key: 'scan:2',
      title: literal('Scan „Serien“'),
      status: 'SUCCEEDED',
      result: {
        sourceId: 2,
        showsAdded: 1,
        showsRemoved: 0,
        added: 3,
        updated: 0,
        removed: 1,
        unchanged: 5,
        nfoErrors: 0,
        unrecognizedFiles: ['ALF/x.avi'],
        details: {
          added: [{ type: 'TV_EPISODE', id: 7, label: 'ALF – S01E03 – Der Fremde' }],
          updated: [],
          removed: [{ type: 'TV_EPISODE', id: null, label: 'ALF – S01E09 – Weg' }],
          addedShows: [{ type: 'TV_SHOW', id: 2, label: 'ALF' }],
          removedShows: [],
          unreadableNfos: [],
        },
      },
    });

    function headings(): (string | null)[] {
      return Array.from(element().querySelectorAll('.scan-report h3')).map(
        (heading) => heading.textContent,
      );
    }

    it('opens by itself when the scan ends and names every entry', async () => {
      await loadSources([series]);
      events.emit({ ...scanJob, status: 'RUNNING', result: null });

      events.emit(scanJob);
      await answerReload([series]);

      expect(headings()).toEqual([
        'Neue Serien (1)',
        'Neue Episoden (3)',
        'Entfernte Episoden (1)',
        'Dateien ohne erkennbare Episode (1)',
      ]);
      const links = Array.from(element().querySelectorAll<HTMLAnchorElement>('.scan-report a'));
      expect(links.map((link) => link.getAttribute('href'))).toEqual(['/tvshows/2', '/episodes/7']);
      const text = element().querySelector('.scan-report')!.textContent;
      expect(text).toContain('ALF – S01E03 – Der Fremde');
      expect(text).toContain('… und 2 weitere');
      expect(text).toContain('ALF – S01E09 – Weg');
      expect(text).toContain('ALF/x.avi');
    });

    it('can be closed and opened again, also after coming back to the page', async () => {
      await loadSources([series]);
      events.emit(scanJob);
      await fixture.whenStable();
      expect(element().querySelector('.scan-report')).toBeNull();

      button('.scan-details-button').click();
      await fixture.whenStable();
      expect(headings()).toContain('Neue Episoden (3)');

      button('.close-scan-report-button').click();
      await fixture.whenStable();
      expect(element().querySelector('.scan-report')).toBeNull();
    });

    it('follows a switch of the UI language', async () => {
      await loadSources([series]);
      events.emit({ ...scanJob, status: 'RUNNING', result: null });
      events.emit(scanJob);
      await answerReload([series]);

      await switchToEnglish();
      await fixture.whenStable();

      expect(headings()).toEqual([
        'New TV shows (1)',
        'New episodes (3)',
        'Removed episodes (1)',
        'Files without a recognizable episode (1)',
      ]);
      expect(element().querySelector('.scan-report')!.textContent).toContain('… and 2 more');
      expect(element().querySelector('.last-scan-result')?.textContent).toBe(
        '3 new, 0 updated, 1 removed, 5 unchanged, 1 new TV show, ' +
          '1 file without a recognizable episode',
      );
    });

    it('offers no details when nothing happened', async () => {
      await loadSources([films]);
      events.emit(job());

      events.emit(
        job({
          status: 'SUCCEEDED',
          result: { sourceId: 1, added: 0, updated: 0, removed: 0, unchanged: 7, nfoErrors: 0 },
        }),
      );
      await answerReload([films]);

      expect(element().querySelector('.scan-details-button')).toBeNull();
      expect(element().querySelector('.scan-report')).toBeNull();
    });
  });

  describe('result of the last scan', () => {
    const counts = { sourceId: 1, updated: 0, removed: 0, unchanged: 7, nfoErrors: 0 };

    function lastScanResults(): (string | null)[] {
      return Array.from(element().querySelectorAll('.last-scan-result')).map(
        (result) => result.textContent,
      );
    }

    it('stays next to the source and updates by itself', async () => {
      await loadSources([films]);
      expect(lastScanResults()).toEqual([]);

      events.emit(job());
      events.emit(job({ status: 'SUCCEEDED', result: { ...counts, added: 3 } }));
      await answerReload([films]);

      expect(lastScanResults()).toEqual(['3 neu, 0 aktualisiert, 0 entfernt, 7 unverändert']);
    });

    it('shows a scan that ended before the page was opened', async () => {
      await loadSources([films, series]);

      events.emit(job({ status: 'SUCCEEDED', result: { ...counts, added: 3 } }));
      await fixture.whenStable();

      expect(snackBarMessages).toEqual([]);
      expect(lastScanResults()).toEqual(['3 neu, 0 aktualisiert, 0 entfernt, 7 unverändert']);
    });

    it('shows the newest finished scan, not one still running', async () => {
      await loadSources([films]);

      events.emit(
        job({
          id: 'old',
          startedAt: '2026-10-25T10:00:00Z',
          status: 'SUCCEEDED',
          result: { ...counts, added: 1 },
        }),
      );
      events.emit(
        job({
          id: 'new',
          startedAt: '2026-10-25T11:00:00Z',
          status: 'SUCCEEDED',
          result: { ...counts, added: 2 },
        }),
      );
      events.emit(job({ id: 'running', startedAt: '2026-10-25T12:00:00Z' }));
      await fixture.whenStable();

      expect(lastScanResults()).toEqual(['2 neu, 0 aktualisiert, 0 entfernt, 7 unverändert']);
    });

    it('shows a failed scan', async () => {
      await loadSources([films]);

      events.emit(job({ status: 'FAILED', message: literal('Verzeichnis weg') }));
      await fixture.whenStable();

      expect(lastScanResults()).toEqual(['Scan „Filme“ fehlgeschlagen: Verzeichnis weg']);
    });
  });

  it('reports failed and cancelled scans', async () => {
    await loadSources([films]);
    events.emit(job());
    events.emit(job({ status: 'FAILED', message: literal('Verzeichnis weg') }));
    await answerReload([films]);
    events.emit(job({ id: 'job-2' }));
    events.emit(job({ id: 'job-2', status: 'CANCELLED' }));
    await answerReload([films]);

    expect(snackBarMessages).toEqual([
      'Scan „Filme“ fehlgeschlagen: Verzeichnis weg',
      'Scan „Filme“ abgebrochen.',
    ]);
  });

  it('ignores jobs of other kinds', async () => {
    await loadSources([films]);
    events.emit(job({ key: 'other:1' }));
    events.emit(job({ key: 'other:1', status: 'SUCCEEDED' }));

    expect(snackBarMessages).toEqual([]);
  });

  it('cancels a running scan', async () => {
    await loadSources([films]);
    events.emit(job());
    fixture.detectChanges();

    button('.cancel-button').click();
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/jobs/job-1/cancel' }).flush(job());

    expect(snackBarMessages).toEqual([]);
  });

  it('reports a scan that cannot be started', async () => {
    await loadSources([films]);

    button('.scan-button').click();
    httpTesting
      .expectOne({ method: 'POST', url: `${SOURCES_URL}/1/scan` })
      .flush({ detail: 'nicht erreichbar' }, { status: 422, statusText: 'Unprocessable' });

    expect(snackBarMessages).toEqual(['nicht erreichbar']);
  });

  it('reports a failed cancellation', async () => {
    await loadSources([films]);
    events.emit(job());
    fixture.detectChanges();

    button('.cancel-button').click();
    httpTesting
      .expectOne({ method: 'POST', url: '/api/v1/jobs/job-1/cancel' })
      .flush({ title: 'Job nicht gefunden' }, { status: 404, statusText: 'Not Found' });

    expect(snackBarMessages).toEqual(['Job nicht gefunden']);
  });

  it('deletes a source after confirmation', async () => {
    await loadSources([films]);

    button('.delete-button').click();
    httpTesting.expectOne({ method: 'DELETE', url: `${SOURCES_URL}/1` }).flush(null);
    await answerReload([]);

    expect(snackBarMessages).toEqual(['Quelle „Filme“ entfernt.']);
  });

  it('keeps the source when deletion is not confirmed', async () => {
    await loadSources([films]);
    confirmAnswer = false;

    button('.delete-button').click();

    httpTesting.expectNone({ method: 'DELETE' });
  });

  it('reports a failed deletion', async () => {
    await loadSources([films]);

    button('.delete-button').click();
    httpTesting
      .expectOne({ method: 'DELETE', url: `${SOURCES_URL}/1` })
      .flush({ title: 'Quelle nicht gefunden' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(snackBarMessages).toEqual(['Quelle nicht gefunden']);
  });

  describe('auto-scrape', () => {
    const scrapeJob = (overrides: Partial<Job> = {}): Job =>
      job({ id: 'job-9', key: 'auto-scrape:1', title: literal('Scrapen „Filme“'), ...overrides });
    const result: AutoScrapeResult = {
      movies: 4,
      scraped: 1,
      unmatched: 1,
      imagesSaved: 9,
      trailersSaved: 2,
      themesSaved: 3,
      enriched: 4,
      unmatchedEntries: [{ type: 'MOVIE', id: 8, label: 'Unbekannt' }],
      failures: [
        {
          key: 'autoScrape.failure',
          args: { entry: literal('Kaputt'), message: literal('TMDB ist nicht erreichbar') },
        },
      ],
      warnings: [{ key: 'autoScrape.noMetadataScraper', args: {} }],
    };

    function texts(selector: string): (string | undefined)[] {
      return Array.from(element().querySelectorAll(selector)).map((item) =>
        item.textContent?.trim(),
      );
    }

    it('summarizes the scrape of a tv source', async () => {
      await loadSources([series]);

      button('.scrape-button').click();
      httpTesting
        .expectOne({ method: 'POST', url: `${SOURCES_URL}/2/auto-scrape` })
        .flush(
          scrapeJob({ id: 'job-10', key: 'auto-scrape:2', title: literal('Scrapen „Serien“') }),
        );
      events.emit(
        scrapeJob({ id: 'job-10', key: 'auto-scrape:2', title: literal('Scrapen „Serien“') }),
      );
      events.emit(
        scrapeJob({
          id: 'job-10',
          key: 'auto-scrape:2',
          title: literal('Scrapen „Serien“'),
          status: 'SUCCEEDED',
          result: {
            shows: 3,
            scraped: 2,
            unmatched: 1,
            episodesUpdated: 40,
            imagesSaved: 12,
            unmatchedEntries: [{ type: 'TV_SHOW', id: 8, label: 'Unbekannt' }],
            failures: [],
            warnings: [],
          },
        }),
      );
      fixture.detectChanges();

      expect(snackBarMessages).toContain(
        'Scrapen „Serien“ abgeschlossen: 3 Serien, 40 Episoden ergänzt, 2 neu zugeordnet, 1 ohne Treffer, ' +
          '12 Bilder gespeichert.',
      );
    });

    it('starts scraping and shows its progress instead of the action buttons', async () => {
      await loadSources([films]);

      button('.scrape-button').click();
      httpTesting
        .expectOne({ method: 'POST', url: `${SOURCES_URL}/1/auto-scrape` })
        .flush(scrapeJob());
      events.emit(scrapeJob({ done: 1, total: 4, message: literal('Heat') }));
      fixture.detectChanges();

      expect(element().querySelector('.scrape-button')).toBeNull();
      expect(element().querySelector('.scan-button')).toBeNull();
      expect(element().querySelector('.job-message')?.textContent).toContain('Heat (25 %)');
    });

    it('cancels a running scrape', async () => {
      await loadSources([films]);
      events.emit(scrapeJob());
      fixture.detectChanges();

      button('.cancel-button').click();

      httpTesting
        .expectOne({ method: 'POST', url: '/api/v1/jobs/job-9/cancel' })
        .flush(scrapeJob());
    });

    it('summarizes the result and lists what still needs attention', async () => {
      await loadSources([films]);
      events.emit(scrapeJob());

      events.emit(scrapeJob({ status: 'SUCCEEDED', result }));
      fixture.detectChanges();

      expect(snackBarMessages).toEqual([
        'Scrapen „Filme“ abgeschlossen: 4 Filme, 1 neu zugeordnet, 1 ohne Treffer, 9 Bilder gespeichert, ' +
          '2 Trailer eingetragen, 3 Titelmusik gespeichert, 4 ergänzt (Bewertungen, Angaben), 1 Fehler.',
      ]);
      expect(texts('.unmatched-entries li')).toEqual(['Unbekannt']);
      expect(element().querySelector('.unmatched-entries a')?.getAttribute('href')).toBe(
        '/movies/8',
      );
      expect(texts('.failures li')).toEqual(['Kaputt: TMDB ist nicht erreichbar']);
      expect(texts('.warnings li')).toEqual([
        'Kein Metadaten-Scraper ist eingerichtet; es werden nur Bilder geholt.',
      ]);
    });

    it('closes the report', async () => {
      await loadSources([films]);
      events.emit(scrapeJob());
      events.emit(scrapeJob({ status: 'SUCCEEDED', result }));
      fixture.detectChanges();

      button('.close-report-button').click();
      fixture.detectChanges();

      expect(element().querySelector('.scrape-report')).toBeNull();
    });

    it('shows no report when everything was done', async () => {
      await loadSources([films]);
      events.emit(scrapeJob());

      events.emit(
        scrapeJob({
          status: 'SUCCEEDED',
          result: {
            ...result,
            unmatched: 0,
            trailersSaved: 0,
            themesSaved: 0,
            enriched: 0,
            unmatchedEntries: [],
            failures: [],
            warnings: [],
          },
        }),
      );
      fixture.detectChanges();

      expect(snackBarMessages).toEqual([
        'Scrapen „Filme“ abgeschlossen: 4 Filme, 1 neu zugeordnet, 0 ohne Treffer, 9 Bilder gespeichert.',
      ]);
      expect(element().querySelector('.scrape-report')).toBeNull();
    });

    it('reports failed scrapes without a report', async () => {
      await loadSources([films]);
      events.emit(scrapeJob());

      events.emit(scrapeJob({ status: 'FAILED', message: literal('Quelle fehlt') }));
      fixture.detectChanges();

      expect(snackBarMessages).toEqual(['Scrapen „Filme“ fehlgeschlagen: Quelle fehlt']);
      expect(element().querySelector('.scrape-report')).toBeNull();
    });

    it('reports a scrape that cannot be started', async () => {
      await loadSources([films]);

      button('.scrape-button').click();
      httpTesting
        .expectOne({ method: 'POST', url: `${SOURCES_URL}/1/auto-scrape` })
        .flush({ detail: 'Läuft bereits' }, { status: 409, statusText: 'Conflict' });

      expect(snackBarMessages).toEqual(['Läuft bereits']);
    });
  });
});
