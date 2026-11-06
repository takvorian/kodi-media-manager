import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';

import { FakeJobEvents, job } from '../jobs/fake-job-events';
import { Job } from '../jobs/job';
import { TranslocoService } from '@jsverse/transloco';

import { provideI18nTesting } from '../i18n/testing';
import { CONFIRM } from '../shared/confirm';
import { EmberImportReport, EmberInspection } from './ember-migration';
import { EmberImportPage, DRY_RUN_TITLE_KEY } from './ember-import-page';
import { EMBER_MIGRATION_URL } from './ember-migration.service';
import { literal } from '../i18n/localized-text';

describe('EmberImportPage', () => {
  const inspection: EmberInspection = {
    profileDirectory: 'C:\\Ember\\Profiles\\Default',
    schemaVersion: 48,
    library: {
      movies: 1169,
      movieSets: 222,
      tvShows: 183,
      episodes: 9516,
      movieSources: 1,
      tvShowSources: 1,
    },
    sources: [
      { name: 'filme', path: 'a:\\medien\\filme' },
      { name: 'ohne Pfad', path: null },
    ],
    tvSources: [
      { name: 'serien', path: 'a:\\medien\\serien' },
      { name: 'gemeinsam', path: 'a:\\medien\\filme' },
    ],
    kodiHosts: [
      { name: 'FireKodi', address: 'firetv.local', port: 8080, pathMappings: 2 },
      { name: null, address: '192.168.1.20', port: 0, pathMappings: 0 },
    ],
    settings: {
      movieLanguage: 'de-DE',
      movieImageLanguage: 'de',
      videoExtensions: ['.mkv', '.avi'],
    },
  };
  const report: EmberImportReport = {
    dryRun: true,
    sourcesCreated: 1,
    sourcesReused: 0,
    moviesCreated: 1169,
    moviesUpdated: 0,
    moviesSkipped: 2,
    moviesWithoutNfo: 1,
    tvShows: {
      showsCreated: 1,
      showsUpdated: 0,
      showsSkipped: 0,
      episodesCreated: 3,
      episodesUpdated: 0,
      episodesSkipped: 1,
      episodesMissing: 2,
    },
    kodiHostsCreated: 2,
    kodiHostsUpdated: 0,
    settingsChanges: ['Sprache der Metadaten: de-DE'],
    skippedSources: ['serien: keine Zuordnung'],
    missingFiles: ['Heat'],
    warnings: ['Verzeichnis nicht erreichbar'],
  };
  const importJob = (overrides: Partial<Job> = {}): Job =>
    job({
      id: 'job-7',
      key: 'ember-import',
      title: { key: DRY_RUN_TITLE_KEY, args: {} },
      ...overrides,
    });

  let fixture: ComponentFixture<EmberImportPage>;
  let httpTesting: HttpTestingController;
  let snackBarMessages: string[];
  let confirmAnswer: boolean;
  let confirmQuestions: string[];
  let events: FakeJobEvents;

  beforeEach(() => {
    snackBarMessages = [];
    confirmAnswer = true;
    confirmQuestions = [];
    events = new FakeJobEvents();
    TestBed.configureTestingModule({
      imports: [EmberImportPage],
      providers: [
        provideI18nTesting(),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: MatSnackBar,
          useValue: { open: (message: string) => snackBarMessages.push(message) },
        },
        {
          provide: CONFIRM,
          useValue: (question: string) => {
            confirmQuestions.push(question);
            return confirmAnswer;
          },
        },
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

  function choose(...paths: string[]): void {
    const picker = element().querySelector<HTMLInputElement>('.profile-picker')!;
    const files = paths.map((path) => {
      const file = new File(['x'], path.split('/').pop()!);
      Object.defineProperty(file, 'webkitRelativePath', { value: path });
      return file;
    });
    Object.defineProperty(picker, 'files', { value: files, configurable: true });
    picker.dispatchEvent(new Event('change'));
  }

  async function analyze(): Promise<void> {
    choose('Ember/Profiles/Default/MyVideos48.emm', 'Ember/Profiles/Default/Settings.xml');
    httpTesting
      .expectOne({ method: 'POST', url: `${EMBER_MIGRATION_URL}/upload` })
      .flush(inspection);
    await fixture.whenStable();
  }

  it('shows the profile and proposes unchanged paths for movie and tv sources', async () => {
    await analyze();

    expect(element().querySelector('.schema')?.textContent).toContain('48');
    expect(element().querySelector('.library')?.textContent).toContain('1169 Filme, 222 Filmsets');
    expect(element().querySelector('.library')?.textContent).toContain(
      '183 Serien mit 9516 Episoden',
    );
    expect(element().querySelector('.settings')?.textContent).toContain('Sprache de-DE');
    expect(field('emberPath0').value).toBe('a:\\medien\\filme');
    expect(field('kmmPath0').value).toBe('a:\\medien\\filme');
    expect(field('emberPath1').value).toBe('a:\\medien\\serien');
    expect(field('emberPath2')).toBeNull();
    expect(texts('.kodi-hosts div')).toEqual([
      'FireKodi (firetv.local:8080)',
      '192.168.1.20 (192.168.1.20:8080)',
    ]);
  });

  it('uploads only the files the import needs', () => {
    choose(
      'Ember/Ember.exe',
      'Ember/Profiles/Default/MyVideos46.emm',
      'Ember/Profiles/Default/MyVideos48.emm',
      'Ember/Profiles/Default/Settings.xml',
      'Ember/Profiles/Default/AdvancedSettings.xml',
      'Ember/Profiles/Default/Interface.Kodi.xml',
      'Ember/Profiles/Default/Core.Mapping.Genres.xml',
      'Ember/Settings.xml',
    );

    const request = httpTesting.expectOne({ method: 'POST', url: `${EMBER_MIGRATION_URL}/upload` });
    const sent = (request.request.body as FormData).getAll('files') as File[];
    expect(sent.map((file) => file.name)).toEqual([
      'MyVideos48.emm',
      'Settings.xml',
      'AdvancedSettings.xml',
      'Interface.Kodi.xml',
    ]);
  });

  it('says so when the chosen folder holds no Ember database', () => {
    choose('Bilder/poster.jpg', 'Bilder/Settings.xml');

    httpTesting.expectNone({ method: 'POST' });
    expect(snackBarMessages[0]).toContain('keine Ember-Datenbank');
  });

  it('discards the uploaded profile', async () => {
    await analyze();
    fixture.detectChanges();

    button('.discard-button').click();
    httpTesting.expectOne({ method: 'DELETE', url: `${EMBER_MIGRATION_URL}/upload` }).flush(null);
    await fixture.whenStable();

    expect(element().querySelector('.dry-run-button')).toBeNull();
    expect(element().querySelector('.discard-button')).toBeNull();
  });

  it('forgets the profile after the real import but keeps it after a dry run', async () => {
    await analyze();

    events.emit(importJob());
    events.emit(importJob({ status: 'SUCCEEDED', result: report }));
    fixture.detectChanges();
    expect(element().querySelector('.import-button')).not.toBeNull();

    events.emit(importJob({ id: 'job-8', title: { key: 'migration.import.title', args: {} } }));
    events.emit(
      importJob({
        id: 'job-8',
        title: { key: 'migration.import.title', args: {} },
        status: 'FAILED',
        message: literal('kaputt'),
      }),
    );
    fixture.detectChanges();
    expect(element().querySelector('.import-button')).toBeNull();
  });

  it('reports a profile that cannot be analyzed', async () => {
    choose('Ember/MyVideos48.emm');
    httpTesting
      .expectOne({ method: 'POST', url: `${EMBER_MIGRATION_URL}/upload` })
      .flush(
        { detail: 'Keine Ember-Datenbank gefunden.' },
        { status: 422, statusText: 'Unprocessable' },
      );
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
      pathMappings: [
        { emberPath: 'a:\\medien\\filme', kmmPath: '/media/filme' },
        { emberPath: 'a:\\medien\\serien', kmmPath: 'a:\\medien\\serien' },
      ],
      importSettings: true,
      importKodiHosts: true,
      importTvShows: true,
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

  it('switches the texts to English at runtime', async () => {
    await analyze();

    TestBed.inject(TranslocoService).setActiveLang('en');
    await fixture.whenStable();
    confirmAnswer = false;
    button('.import-button').click();

    expect(element().querySelector('.page-title')?.textContent).toBe('Ember import');
    expect(element().querySelector('.library')?.textContent?.trim()).toBe(
      '1,169 movies, 222 movie sets, 183 TV shows with 9,516 episodes',
    );
    expect(texts('.dry-run-button')).toEqual(['Dry run']);
    expect(confirmQuestions).toEqual([
      'Import 1,169 movies and 183 TV shows from Ember into the library?',
    ]);
  });

  it('asks before the real import and can be declined', async () => {
    await analyze();
    confirmAnswer = false;

    button('.import-button').click();

    httpTesting.expectNone({ method: 'POST' });
    expect(confirmQuestions).toEqual([
      '1169 Filme und 183 Serien aus Ember in die Bibliothek übernehmen?',
    ]);
  });

  it('imports without settings, tv shows and kodi hosts after confirmation', async () => {
    await analyze();
    element().querySelector<HTMLInputElement>('.settings-checkbox input')!.click();
    element().querySelector<HTMLInputElement>('.kodi-hosts-checkbox input')!.click();
    element().querySelector<HTMLInputElement>('.tv-shows-checkbox input')!.click();
    await fixture.whenStable();

    button('.import-button').click();
    const request = httpTesting.expectOne({ method: 'POST', url: `${EMBER_MIGRATION_URL}/import` });
    request.flush(importJob({ title: { key: 'migration.import.title', args: {} } }));

    expect(request.request.body.dryRun).toBe(false);
    expect(request.request.body.importSettings).toBe(false);
    expect(request.request.body.importKodiHosts).toBe(false);
    expect(request.request.body.importTvShows).toBe(false);
    expect(confirmQuestions).toEqual(['1169 Filme aus Ember in die Bibliothek übernehmen?']);
  });

  it('shows progress, can cancel and presents the report', async () => {
    await analyze();
    events.emit(importJob({ done: 100, total: 1169, message: literal('Heat') }));
    fixture.detectChanges();

    expect(element().querySelector('.job-message')?.textContent).toContain('Heat (9 %)');
    button('.cancel-button').click();
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/jobs/job-7/cancel' }).flush(importJob());

    events.emit(importJob({ status: 'SUCCEEDED', result: report }));
    fixture.detectChanges();

    expect(snackBarMessages).toEqual([
      'Ember-Import (Probelauf): würde übernehmen 1169 neue und 0 vorhandene Filme sowie 3 Episoden.',
    ]);
    expect(element().querySelector('.report mat-card-title')?.textContent).toContain('Probelauf');
    expect(element().querySelector('.report-movies')?.textContent).toContain(
      '1169 neu, 0 aktualisiert, 2 übersprungen',
    );
    expect(texts('.settings-changes li')).toEqual(['Sprache der Metadaten: de-DE']);
    expect(element().querySelector('.report-kodi-hosts')?.textContent).toContain(
      '2 neu, 0 aktualisiert',
    );
    expect(element().querySelector('.report-tv-shows')?.textContent).toContain(
      '1 neu, 0 aktualisiert, 0 übersprungen',
    );
    expect(element().querySelector('.report-episodes')?.textContent).toContain(
      '3 neu, 0 aktualisiert, 1 übersprungen, 2 ohne Datei',
    );
    expect(texts('.skipped-sources li')).toEqual(['serien: keine Zuordnung']);
    expect(texts('.warnings li')).toEqual(['Verzeichnis nicht erreichbar']);
    expect(texts('.missing-files li')).toEqual(['Heat']);
  });

  it('hides tv show rows when no tv shows were imported', async () => {
    await analyze();
    const withoutTvShows: EmberImportReport = {
      ...report,
      tvShows: {
        showsCreated: 0,
        showsUpdated: 0,
        showsSkipped: 0,
        episodesCreated: 0,
        episodesUpdated: 0,
        episodesSkipped: 0,
        episodesMissing: 0,
      },
    };

    events.emit(importJob());
    events.emit(importJob({ status: 'SUCCEEDED', result: withoutTvShows }));
    fixture.detectChanges();

    expect(element().querySelector('.report-tv-shows')).toBeNull();
    expect(snackBarMessages).toEqual([
      'Ember-Import (Probelauf): würde übernehmen 1169 neue und 0 vorhandene Filme.',
    ]);
  });

  it('reports failed, cancelled and unstartable imports', async () => {
    await analyze();
    events.emit(importJob());
    events.emit(importJob({ status: 'FAILED', message: literal('Datenbank gesperrt') }));
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
