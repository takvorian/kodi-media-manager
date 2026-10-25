import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';

import { FakeJobEvents, job } from '../jobs/fake-job-events';
import { Job } from '../jobs/job';
import { CONFIRM } from '../shared/confirm';
import { EmberImportReport, EmberInspection } from './ember-migration';
import { EmberImportPage } from './ember-import-page';
import { EMBER_MIGRATION_URL } from './ember-migration.service';

describe('EmberImportPage', () => {
  const inspection: EmberInspection = {
    profileDirectory: 'C:\\Ember\\Profiles\\Default',
    schemaVersion: 48,
    library: { movies: 1169, movieSets: 222, tvShows: 183, episodes: 9516, movieSources: 1, tvShowSources: 1 },
    sources: [
      { name: 'filme', path: 'a:\\medien\\filme' },
      { name: 'ohne Pfad', path: null },
    ],
    kodiHosts: [
      { name: 'FireKodi', address: 'firetv.local', port: 8080, pathMappings: 2 },
      { name: null, address: '192.168.1.20', port: 0, pathMappings: 0 },
    ],
    settings: { movieLanguage: 'de-DE', movieImageLanguage: 'de', videoExtensions: ['.mkv', '.avi'] },
  };
  const report: EmberImportReport = {
    dryRun: true,
    sourcesCreated: 1,
    sourcesReused: 0,
    moviesCreated: 1169,
    moviesUpdated: 0,
    moviesSkipped: 2,
    moviesWithoutNfo: 1,
    kodiHostsCreated: 2,
    kodiHostsUpdated: 0,
    settingsChanges: ['Sprache der Metadaten: de-DE'],
    skippedSources: ['serien: keine Zuordnung'],
    missingFiles: ['Heat'],
    warnings: ['Verzeichnis nicht erreichbar'],
  };
  const importJob = (overrides: Partial<Job> = {}): Job =>
    job({ id: 'job-7', key: 'ember-import', title: 'Ember-Import (Probelauf)', ...overrides });

  let fixture: ComponentFixture<EmberImportPage>;
  let httpTesting: HttpTestingController;
  let snackBarMessages: string[];
  let confirmAnswer: boolean;
  let events: FakeJobEvents;

  beforeEach(() => {
    snackBarMessages = [];
    confirmAnswer = true;
    events = new FakeJobEvents();
    TestBed.configureTestingModule({
      imports: [EmberImportPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatSnackBar, useValue: { open: (message: string) => snackBarMessages.push(message) } },
        { provide: CONFIRM, useValue: () => confirmAnswer },
        events.provider(),
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EmberImportPage);
    fixture.detectChanges();
  });

  afterEach(() => httpTesting.verify());

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function field(name: string): HTMLInputElement {
    return element().querySelector<HTMLInputElement>(`[name="${name}"]`)!;
  }

  function button(selector: string): HTMLButtonElement {
    return element().querySelector<HTMLButtonElement>(selector)!;
  }

  function texts(selector: string): (string | undefined)[] {
    return Array.from(element().querySelectorAll(selector)).map((item) => item.textContent?.trim());
  }

  async function analyze(): Promise<void> {
    field('profilePath').value = ' C:\\Ember ';
    field('profilePath').dispatchEvent(new Event('input'));
    await fixture.whenStable();
    button('.inspect-button').click();
    httpTesting.expectOne((request) => request.url === `${EMBER_MIGRATION_URL}/inspect`).flush(inspection);
    await fixture.whenStable();
  }

  it('shows the profile and proposes unchanged paths for the sources', async () => {
    await analyze();

    expect(element().querySelector('.library')?.textContent).toContain('1169 Filme, 222 Filmsets');
    expect(element().querySelector('.settings')?.textContent).toContain('Sprache de-DE');
    expect(field('emberPath0').value).toBe('a:\\medien\\filme');
    expect(field('kmmPath0').value).toBe('a:\\medien\\filme');
    expect(field('emberPath1')).toBeNull();
    expect(texts('.kodi-hosts div')).toEqual(['FireKodi (firetv.local:8080)', '192.168.1.20 (192.168.1.20:8080)']);
  });

  it('reports a profile that cannot be analyzed', async () => {
    field('profilePath').value = 'C:\\leer';
    field('profilePath').dispatchEvent(new Event('input'));
    await fixture.whenStable();

    button('.inspect-button').click();
    httpTesting
      .expectOne((request) => request.url === `${EMBER_MIGRATION_URL}/inspect`)
      .flush({ detail: 'Keine Ember-Datenbank gefunden.' }, { status: 422, statusText: 'Unprocessable' });
    await fixture.whenStable();

    expect(snackBarMessages).toEqual(['Keine Ember-Datenbank gefunden.']);
    expect(element().querySelector('.dry-run-button')).toBeNull();
  });

  it('starts a dry run with the edited mapping', async () => {
    await analyze();
    field('kmmPath0').value = ' /media/filme ';
    field('kmmPath0').dispatchEvent(new Event('input'));
    await fixture.whenStable();

    button('.dry-run-button').click();
    const request = httpTesting.expectOne({ method: 'POST', url: `${EMBER_MIGRATION_URL}/import` });
    request.flush(importJob());

    expect(request.request.body).toEqual({
      profilePath: 'C:\\Ember\\Profiles\\Default',
      pathMappings: [{ emberPath: 'a:\\medien\\filme', kmmPath: '/media/filme' }],
      importSettings: true,
      importKodiHosts: true,
      dryRun: true,
    });
  });

  it('requires a target path for every source', async () => {
    await analyze();
    field('kmmPath0').value = '';
    field('kmmPath0').dispatchEvent(new Event('input'));
    await fixture.whenStable();

    button('.dry-run-button').click();

    httpTesting.expectNone({ method: 'POST' });
  });

  it('asks before the real import and can be declined', async () => {
    await analyze();
    confirmAnswer = false;

    button('.import-button').click();

    httpTesting.expectNone({ method: 'POST' });
  });

  it('imports without settings after confirmation', async () => {
    await analyze();
    element().querySelector<HTMLInputElement>('mat-checkbox input')!.click();
    element().querySelector<HTMLInputElement>('.kodi-hosts-checkbox input')!.click();
    await fixture.whenStable();

    button('.import-button').click();
    const request = httpTesting.expectOne({ method: 'POST', url: `${EMBER_MIGRATION_URL}/import` });
    request.flush(importJob({ title: 'Ember-Import' }));

    expect(request.request.body.dryRun).toBe(false);
    expect(request.request.body.importSettings).toBe(false);
    expect(request.request.body.importKodiHosts).toBe(false);
  });

  it('shows progress, can cancel and presents the report', async () => {
    await analyze();
    events.emit(importJob({ done: 100, total: 1169, message: 'Heat' }));
    fixture.detectChanges();

    expect(element().querySelector('.job-message')?.textContent).toContain('Heat (9 %)');
    button('.cancel-button').click();
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/jobs/job-7/cancel' }).flush(importJob());

    events.emit(importJob({ status: 'SUCCEEDED', result: report }));
    fixture.detectChanges();

    expect(snackBarMessages).toEqual(['Ember-Import (Probelauf): würde übernehmen 1169 neue und 0 vorhandene Filme.']);
    expect(element().querySelector('.report mat-card-title')?.textContent).toContain('Probelauf');
    expect(element().querySelector('.report-movies')?.textContent).toContain('1169 neu, 0 aktualisiert, 2 übersprungen');
    expect(texts('.settings-changes li')).toEqual(['Sprache der Metadaten: de-DE']);
    expect(element().querySelector('.report-kodi-hosts')?.textContent).toContain('2 neu, 0 aktualisiert');
    expect(texts('.skipped-sources li')).toEqual(['serien: keine Zuordnung']);
    expect(texts('.warnings li')).toEqual(['Verzeichnis nicht erreichbar']);
    expect(texts('.missing-files li')).toEqual(['Heat']);
  });

  it('reports failed, cancelled and unstartable imports', async () => {
    await analyze();
    events.emit(importJob());
    events.emit(importJob({ status: 'FAILED', message: 'Datenbank gesperrt' }));
    events.emit(importJob({ id: 'job-8' }));
    events.emit(importJob({ id: 'job-8', status: 'CANCELLED' }));
    fixture.detectChanges();
    button('.dry-run-button').click();
    httpTesting
      .expectOne({ method: 'POST', url: `${EMBER_MIGRATION_URL}/import` })
      .flush({ detail: 'Läuft bereits' }, { status: 409, statusText: 'Conflict' });

    expect(snackBarMessages).toEqual([
      'Ember-Import (Probelauf) fehlgeschlagen: Datenbank gesperrt',
      'Ember-Import (Probelauf) abgebrochen – es wurde nichts übernommen.',
      'Läuft bereits',
    ]);
  });

  it('reports a failed cancellation', async () => {
    await analyze();
    events.emit(importJob());
    fixture.detectChanges();

    button('.cancel-button').click();
    httpTesting
      .expectOne({ method: 'POST', url: '/api/v1/jobs/job-7/cancel' })
      .flush({ title: 'Job nicht gefunden' }, { status: 404, statusText: 'Not Found' });

    expect(snackBarMessages).toEqual(['Job nicht gefunden']);
  });
});
