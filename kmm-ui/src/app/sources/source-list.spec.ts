import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';

import { FakeJobEvents, job } from '../jobs/fake-job-events';
import { Job } from '../jobs/job';
import { CONFIRM } from '../shared/confirm';
import { AutoScrapeResult, MediaSource } from './media-source';
import { SOURCES_URL } from './media-source.service';
import { SourceList } from './source-list';

describe('SourceList', () => {
  const films: MediaSource = {
    id: 1,
    name: 'Filme',
    path: '/media/filme',
    mediaType: 'MOVIE',
    createdAt: '2026-10-25T10:00:00Z',
    lastScannedAt: null,
  };
  const series: MediaSource = { ...films, id: 2, name: 'Serien', path: '/media/serien', mediaType: 'TV_SHOW' };

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
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatSnackBar, useValue: { open: (message: string) => snackBarMessages.push(message) } },
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

  /** Beantwortet das (Neu-)Laden der Liste, das ein Resource-Reload beim nächsten Rendern auslöst. */
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

  async function fillForm(name: string, path: string): Promise<void> {
    for (const [field, value] of [['name', name], ['path', path]]) {
      const input = element().querySelector<HTMLInputElement>(`input[name="${field}"]`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    await fixture.whenStable();
  }

  it('lists the sources', async () => {
    await loadSources([films, series]);

    expect(Array.from(element().querySelectorAll('.source-name')).map((cell) => cell.textContent)).toEqual([
      'Filme',
      'Serien',
    ]);
  });

  it('offers scanning for movie and TV sources but scraping only for movies', async () => {
    await loadSources([films, series, { ...films, id: 3, name: 'Sets', mediaType: 'MOVIE_SET' }]);

    expect(element().querySelectorAll('.scan-button')).toHaveLength(2);
    expect(element().querySelectorAll('.scrape-button')).toHaveLength(2);
  });

  it('reports episodes without recognizable number after a TV scan', async () => {
    await loadSources([series]);
    events.emit(job({ key: 'scan:2', title: 'Scan „Serien“' }));

    events.emit(
      job({
        key: 'scan:2',
        title: 'Scan „Serien“',
        status: 'SUCCEEDED',
        result: { sourceId: 2, added: 4, updated: 0, removed: 0, unchanged: 0, nfoErrors: 0, unrecognizedFiles: ['x.avi'] },
      }),
    );
    await answerReload([series]);

    expect(snackBarMessages).toEqual([
      'Scan „Serien“ abgeschlossen: 4 neu, 0 aktualisiert, 0 entfernt, 0 unverändert, 1 Dateien ohne erkennbare Episode.',
    ]);
  });

  it('lets Kodi scan movie and tv sources', async () => {
    await loadSources([films, series]);
    expect(element().querySelectorAll('.kodi-scan-button')).toHaveLength(2);

    element().querySelector<HTMLButtonElement>('.kodi-scan-button')!.click();
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/kodi/sources/1/scan' }).flush([
      { hostId: 1, host: 'Wohnzimmer', outcome: 'SCAN_STARTED', message: 'Kodi sucht nach neuen Filmen.', at: '' },
    ]);
    element().querySelector<HTMLButtonElement>('.kodi-scan-button')!.click();
    httpTesting
      .expectOne({ method: 'POST', url: '/api/v1/kodi/sources/1/scan' })
      .flush({ detail: 'Quelle nicht gefunden' }, { status: 404, statusText: 'Not Found' });

    expect(snackBarMessages).toEqual(['Wohnzimmer: Kodi sucht nach neuen Filmen.', 'Quelle nicht gefunden']);
  });

  it('imports the watched status of a source from Kodi as job', async () => {
    await loadSources([films, series]);
    const playbackJob = job({ id: 'job-7', key: 'kodi-playback:1', title: 'Gesehen-Status „Filme“ aus Kodi' });

    button('.kodi-playback-button').click();
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/kodi/sources/1/playback' }).flush(playbackJob);
    fixture.detectChanges();
    expect(element().querySelector('.job-progress')).not.toBeNull();
    events.emit({
      ...playbackJob,
      status: 'SUCCEEDED',
      result: { sourceId: 1, host: 'Wohnzimmer', updated: 1, unchanged: 2, kept: 0, notInKodi: 0, failed: 0, failures: [] },
    });
    fixture.detectChanges();

    expect(snackBarMessages).toEqual([
      'Gesehen-Status „Filme“ aus Kodi (Wohnzimmer) abgeschlossen: 1 übernommen, 2 unverändert, 0 in Kodi unbekannt.',
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
    expect(request.request.body).toEqual({ name: 'Filme', path: '/media/filme', mediaType: 'MOVIE' });
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

  it('shows the problem detail when creating fails', async () => {
    await loadSources([]);
    await fillForm('Filme', '/fehlt');

    button('.create-button').click();
    httpTesting
      .expectOne({ method: 'POST', url: SOURCES_URL })
      .flush({ detail: "'/fehlt' ist kein erreichbares Verzeichnis." }, { status: 422, statusText: 'Unprocessable' });
    await fixture.whenStable();

    expect(snackBarMessages).toEqual(["'/fehlt' ist kein erreichbares Verzeichnis."]);
    expect(button('.create-button').disabled).toBe(false);
  });

  it('starts a scan and shows its progress instead of the scan button', async () => {
    await loadSources([films]);

    button('.scan-button').click();
    httpTesting.expectOne({ method: 'POST', url: `${SOURCES_URL}/1/scan` }).flush(job());
    events.emit(job({ done: 1, total: 4, message: 'NFOs werden geprüft' }));
    fixture.detectChanges();

    expect(element().querySelector('.scan-button')).toBeNull();
    expect(element().querySelector('.job-message')?.textContent).toContain('NFOs werden geprüft (25 %)');
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
    await answerReload([{ ...films, lastScannedAt: '2026-10-25T12:00:00Z' }]);

    expect(snackBarMessages).toEqual([
      'Scan „Filme“ abgeschlossen: 3 neu, 2 aktualisiert, 1 entfernt, 7 unverändert, 1 NFO-Fehler.',
    ]);
    expect(element().querySelector('.scan-button')).not.toBeNull();
  });

  it('reports failed and cancelled scans', async () => {
    await loadSources([films]);
    events.emit(job());
    events.emit(job({ status: 'FAILED', message: 'Verzeichnis weg' }));
    await answerReload([films]);
    events.emit(job({ id: 'job-2' }));
    events.emit(job({ id: 'job-2', status: 'CANCELLED' }));
    await answerReload([films]);

    expect(snackBarMessages).toEqual(['Scan „Filme“ fehlgeschlagen: Verzeichnis weg', 'Scan „Filme“ abgebrochen.']);
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
      job({ id: 'job-9', key: 'auto-scrape:1', title: 'Scrapen „Filme“', ...overrides });
    const result: AutoScrapeResult = {
      movies: 4,
      scraped: 1,
      unmatched: 1,
      imagesSaved: 9,
      unmatchedTitles: ['Unbekannt'],
      failures: ['Kaputt: TMDB ist nicht erreichbar'],
      warnings: ['fanart.tv: Key fehlt'],
    };

    function texts(selector: string): (string | undefined)[] {
      return Array.from(element().querySelectorAll(selector)).map((item) => item.textContent?.trim());
    }

    it('summarizes the scrape of a tv source', async () => {
      await loadSources([series]);

      button('.scrape-button').click();
      httpTesting.expectOne({ method: 'POST', url: `${SOURCES_URL}/2/auto-scrape` })
        .flush(scrapeJob({ id: 'job-10', key: 'auto-scrape:2', title: 'Scrapen „Serien“' }));
      events.emit(scrapeJob({ id: 'job-10', key: 'auto-scrape:2', title: 'Scrapen „Serien“' }));
      events.emit(
        scrapeJob({
          id: 'job-10',
          key: 'auto-scrape:2',
          title: 'Scrapen „Serien“',
          status: 'SUCCEEDED',
          result: {
            shows: 3,
            scraped: 2,
            unmatched: 1,
            episodesUpdated: 40,
            imagesSaved: 12,
            unmatchedTitles: ['Unbekannt'],
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
      httpTesting.expectOne({ method: 'POST', url: `${SOURCES_URL}/1/auto-scrape` }).flush(scrapeJob());
      events.emit(scrapeJob({ done: 1, total: 4, message: 'Heat' }));
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

      httpTesting.expectOne({ method: 'POST', url: '/api/v1/jobs/job-9/cancel' }).flush(scrapeJob());
    });

    it('summarizes the result and lists what still needs attention', async () => {
      await loadSources([films]);
      events.emit(scrapeJob());

      events.emit(scrapeJob({ status: 'SUCCEEDED', result }));
      fixture.detectChanges();

      expect(snackBarMessages).toEqual([
        'Scrapen „Filme“ abgeschlossen: 4 Filme, 1 neu zugeordnet, 1 ohne Treffer, 9 Bilder gespeichert, 1 Fehler.',
      ]);
      expect(texts('.unmatched-titles li')).toEqual(['Unbekannt']);
      expect(texts('.failures li')).toEqual(['Kaputt: TMDB ist nicht erreichbar']);
      expect(texts('.warnings li')).toEqual(['fanart.tv: Key fehlt']);
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
          result: { ...result, unmatched: 0, unmatchedTitles: [], failures: [], warnings: [] },
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

      events.emit(scrapeJob({ status: 'FAILED', message: 'Quelle fehlt' }));
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
